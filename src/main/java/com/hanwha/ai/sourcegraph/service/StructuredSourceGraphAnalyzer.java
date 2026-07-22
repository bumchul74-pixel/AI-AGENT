package com.hanwha.ai.sourcegraph.service;

import com.hanwha.ai.sourcegraph.domain.SourceGraphIdentity;
import com.hanwha.ai.sourcegraph.domain.SourceOntology;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Extracts normalized ontology entities from non-Java source documents. */
public class StructuredSourceGraphAnalyzer {
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([A-Za-z0-9_.$]+)"
    );
    private static final Pattern COLUMN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SQL_WORDS = Set.of(
            "select", "from", "where", "join", "on", "insert", "into", "values", "update",
            "set", "delete", "and", "or", "as", "null", "true", "false", "asc", "desc"
    );

    public boolean supports(String fileName) {
        String extension = extension(fileName);
        return ".xml".equals(extension) || ".yml".equals(extension)
                || ".yaml".equals(extension) || ".md".equals(extension);
    }

    public SourceGraphResponse analyze(JavaSourceGraphIngestRequest request) {
        Context context = context(request);
        Graph graph = baseGraph(context);
        switch (extension(context.fileName())) {
            case ".xml" -> analyzeMapperXml(context, graph);
            case ".yml", ".yaml" -> analyzeConfiguration(context, graph);
            case ".md" -> analyzeStandardDocument(context, graph);
            default -> throw new IllegalArgumentException("Unsupported structured source: " + context.fileName());
        }
        return graph.response();
    }

    private Graph baseGraph(Context context) {
        Graph graph = new Graph(context);
        String projectUid = SourceGraphIdentity.projectUid(context.projectId());
        String moduleUid = SourceGraphIdentity.moduleUid(context.projectId(), context.moduleName());
        String fileUid = SourceGraphIdentity.sourceFileUid(context.projectId(), context.filePath());
        graph.node(context.documentUid(), "Document", context.fileName(), Map.of(
                "fileName", context.fileName(), "filePath", context.filePath()
        ));
        graph.node(projectUid, "Project", context.projectId(), Map.of());
        graph.node(moduleUid, "Module", context.moduleName(), Map.of());
        graph.node(fileUid, "SourceFile", context.fileName(), Map.of(
                "fileName", context.fileName(), "filePath", context.filePath(),
                "format", extension(context.fileName()).substring(1)
        ));
        graph.relationship(projectUid, moduleUid, "CONTAINS");
        graph.relationship(moduleUid, fileUid, "CONTAINS");
        graph.relationship(context.documentUid(), fileUid, "HAS_SOURCE");
        for (String chunkId : context.chunkIds()) {
            graph.node(chunkId, "Chunk", chunkId, Map.of("chunkId", chunkId));
            graph.relationship(context.documentUid(), chunkId, "CONTAINS");
            graph.relationship(chunkId, fileUid, "DESCRIBES");
        }
        return graph;
    }

    private void analyzeMapperXml(Context context, Graph graph) {
        Element mapper = xmlRoot(context.content());
        if (mapper == null || !"mapper".equals(mapper.getTagName())) {
            throw new IllegalArgumentException("MyBatis mapper XML root element is required.");
        }
        String namespace = mapper.getAttribute("namespace").trim();
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException("MyBatis mapper namespace is required.");
        }
        String mapperUid = SourceGraphIdentity.typeUid(context.projectId(), namespace);
        String fileUid = SourceGraphIdentity.sourceFileUid(context.projectId(), context.filePath());
        graph.node(mapperUid, "JavaType", simpleName(namespace), Map.of(
                "fqn", namespace, "simpleName", simpleName(namespace), "kind", "INTERFACE",
                "layer", "Mapper", "external", true
        ));
        graph.relationship(fileUid, mapperUid, "HAS_MAPPER_XML");

        for (String tag : List.of("select", "insert", "update", "delete")) {
            NodeList statements = mapper.getElementsByTagName(tag);
            for (int index = 0; index < statements.getLength(); index++) {
                Element statement = (Element) statements.item(index);
                String statementId = statement.getAttribute("id").trim();
                if (!StringUtils.hasText(statementId)) {
                    continue;
                }
                String sql = statement.getTextContent().replaceAll("\\s+", " ").trim();
                String operation = "select".equals(tag) ? "READ" : "WRITE";
                String statementUid = SourceGraphIdentity.sqlStatementUid(
                        context.projectId(), namespace, statementId
                );
                graph.node(statementUid, "SqlStatement", statementId, Map.of(
                        "statementId", statementId, "namespace", namespace,
                        "operation", operation, "sql", sql, "sourceFormat", "mybatis-xml"
                ));
                graph.relationship(mapperUid, statementUid, "HAS_STATEMENT");
                addSqlEntities(context, graph, statementUid, mapperUid, sql, operation);
            }
        }
    }

    private void addSqlEntities(Context context, Graph graph, String statementUid,
                                String mapperUid, String sql, String operation) {
        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        Set<String> tables = new LinkedHashSet<>();
        while (tableMatcher.find()) {
            tables.add(cleanIdentifier(tableMatcher.group(1)));
        }
        for (String qualifiedTable : tables) {
            if (!StringUtils.hasText(qualifiedTable)) {
                continue;
            }
            int separator = qualifiedTable.lastIndexOf('.');
            String schema = separator >= 0 ? qualifiedTable.substring(0, separator) : "public";
            String table = separator >= 0 ? qualifiedTable.substring(separator + 1) : qualifiedTable;
            String tableUid = SourceGraphIdentity.tableUid(context.projectId(), schema, table);
            graph.node(tableUid, "DatabaseTable", qualifiedTable, Map.of(
                    "datasourceId", context.projectId(), "schema", schema, "tableName", table
            ));
            graph.relationship(statementUid, tableUid, "READ".equals(operation) ? "READS_FROM" : "WRITES_TO");
            graph.relationship(mapperUid, tableUid, "MAPS_TO");
            for (String column : sqlColumns(sql, tables)) {
                String columnUid = SourceGraphIdentity.columnUid(context.projectId(), schema, table, column);
                graph.node(columnUid, "DatabaseColumn", column, Map.of(
                        "datasourceId", context.projectId(), "schema", schema,
                        "tableName", table, "columnName", column
                ));
                graph.relationship(tableUid, columnUid, "HAS_COLUMN");
                graph.relationship(statementUid, columnUid, "REFERENCES_COLUMN");
            }
        }
    }

    private List<String> sqlColumns(String sql, Set<String> tables) {
        String withoutParameters = sql.replaceAll("[#][$]?[{][^}]+[}]", " ");
        Matcher matcher = COLUMN_PATTERN.matcher(withoutParameters);
        Set<String> columns = new LinkedHashSet<>();
        Set<String> tableParts = new LinkedHashSet<>();
        tables.forEach(table -> {
            for (String part : table.split("\\.")) {
                tableParts.add(part.toLowerCase(Locale.ROOT));
            }
        });
        while (matcher.find()) {
            String candidate = matcher.group();
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (!SQL_WORDS.contains(lower) && !tableParts.contains(lower)) {
                columns.add(candidate);
            }
        }
        return List.copyOf(columns);
    }

    private Element xmlRoot(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content))).getDocumentElement();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid MyBatis mapper XML.", exception);
        }
    }

    private void analyzeConfiguration(Context context, Graph graph) {
        String configurationUid = SourceGraphIdentity.configurationUid(context.projectId(), context.filePath());
        String fileUid = SourceGraphIdentity.sourceFileUid(context.projectId(), context.filePath());
        graph.node(configurationUid, "ConfigurationFile", context.fileName(), Map.of(
                "filePath", context.filePath(), "format", "yaml"
        ));
        graph.relationship(fileUid, configurationUid, "DEFINES");
        for (YamlProperty property : yamlProperties(context.content())) {
            String propertyUid = SourceGraphIdentity.configurationPropertyUid(context.projectId(), property.key());
            graph.node(propertyUid, "ConfigurationProperty", property.key(), Map.of(
                    "key", property.key(), "value", safeConfigurationValue(property.key(), property.value())
            ));
            graph.relationship(configurationUid, propertyUid, "DEFINES");
            if (isProfileKey(property.key())) {
                String profiles = property.value().replace("[", "").replace("]", "");
                for (String profile : profiles.split(",")) {
                    String normalized = unquote(profile.trim());
                    if (StringUtils.hasText(normalized)) {
                        String profileUid = SourceGraphIdentity.profileUid(context.projectId(), normalized);
                        graph.node(profileUid, "Profile", normalized, Map.of("profileName", normalized));
                        graph.relationship(configurationUid, profileUid, "ACTIVATES");
                    }
                }
            }
            if (property.key().toLowerCase(Locale.ROOT).endsWith(".provider")) {
                String provider = configurationLiteral(property.value());
                if (StringUtils.hasText(provider)) {
                    String providerUid = SourceGraphIdentity.providerUid(context.projectId(), provider);
                    graph.node(providerUid, "Provider", provider, Map.of("providerName", provider));
                    graph.relationship(configurationUid, providerUid, "CONFIGURES");
                }
            }
        }
        for (String beanName : yamlBeanNames(context.content())) {
            String beanUid = SourceGraphIdentity.beanUid(context.projectId(), beanName);
            graph.node(beanUid, "Bean", beanName, Map.of("beanName", beanName));
            graph.relationship(configurationUid, beanUid, "CONFIGURES");
        }
    }

    private void analyzeStandardDocument(Context context, Graph graph) {
        String section = "General";
        int index = 0;
        for (String rawLine : context.content().split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("#")) {
                section = line.replaceFirst("^#+\\s*", "").trim();
                continue;
            }
            if (!line.matches("^(?:[-*]|[0-9]+[.)])\\s+.+")) {
                continue;
            }
            String statement = line.replaceFirst("^(?:[-*]|[0-9]+[.)])\\s+", "").trim();
            boolean template = containsAny((section + " " + statement).toLowerCase(Locale.ROOT),
                    "template", "템플릿", "boilerplate");
            String stableId = stableId(section + statement + index++);
            String uid = template
                    ? "template:" + context.projectId() + ":" + stableId
                    : SourceGraphIdentity.standardRuleUid(context.projectId(), stableId);
            String label = template ? "StandardTemplate" : "StandardRule";
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("section", section);
            properties.put("statement", abbreviate(statement));
            properties.put(template ? "templateType" : "ruleType", inferRuleType(section));
            graph.node(uid, label, abbreviate(statement), properties);
            graph.relationship(context.documentUid(), uid, "DEFINES");
        }
    }

    private Context context(JavaSourceGraphIngestRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("Source document content is required.");
        }
        String source = firstText(request.source(), request.fileName(), "structured-source");
        String fileName = firstText(request.fileName(), source, "source.xml");
        String projectId = SourceGraphIdentity.projectId(request.projectId());
        String moduleName = SourceGraphIdentity.moduleName(request.moduleName());
        String filePath = SourceGraphIdentity.normalizeFilePath(request.filePath(), fileName);
        String graphKey = "rag-source:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        String documentUid = source.startsWith("document:")
                ? source : "document:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        return new Context(graphKey, projectId, moduleName, fileName, filePath, request.fileHash(),
                source, documentUid, request.content(), request.chunkIds() == null ? List.of() : request.chunkIds(),
                Instant.now().toString(), extractor(fileName));
    }

    private List<YamlProperty> yamlProperties(String content) {
        List<YamlProperty> properties = new ArrayList<>();
        Deque<YamlLevel> path = new ArrayDeque<>();
        for (String rawLine : content.split("\\R")) {
            if (rawLine.isBlank() || rawLine.stripLeading().startsWith("#")
                    || rawLine.stripLeading().startsWith("-")) {
                continue;
            }
            int indent = rawLine.length() - rawLine.stripLeading().length();
            String line = rawLine.trim();
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            while (!path.isEmpty() && path.peekLast().indent() >= indent) {
                path.removeLast();
            }
            String key = unquote(line.substring(0, separator).trim());
            String value = line.substring(separator + 1).trim();
            String prefix = path.stream().map(YamlLevel::key)
                    .reduce((left, right) -> left + "." + right).orElse("");
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (value.isEmpty()) {
                path.addLast(new YamlLevel(indent, key));
            } else {
                properties.add(new YamlProperty(fullKey, value.replaceFirst("\\s+#.*$", "").trim()));
            }
        }
        return properties;
    }

    private Set<String> yamlBeanNames(String content) {
        Set<String> names = new LinkedHashSet<>();
        for (YamlProperty property : yamlProperties(content)) {
            String[] parts = property.key().split("\\.");
            for (int index = 0; index < parts.length - 1; index++) {
                if ("beans".equalsIgnoreCase(parts[index])) {
                    names.add(parts[index + 1]);
                }
            }
        }
        return names;
    }

    private boolean isProfileKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.endsWith("profiles.active") || normalized.endsWith("activate.on-profile");
    }

    private String safeConfigurationValue(String key, String value) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "password", "secret", "token", "api-key", "apikey", "credential")) {
            return "[REDACTED]";
        }
        return abbreviate(unquote(value));
    }

    private String configurationLiteral(String value) {
        String normalized = unquote(value);
        if (normalized.startsWith("${") && normalized.endsWith("}") && normalized.contains(":")) {
            return normalized.substring(normalized.indexOf(':') + 1, normalized.length() - 1);
        }
        return normalized;
    }

    private String inferRuleType(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "architecture", "아키텍처", "layer", "계층")) {
            return "ARCHITECTURE";
        }
        if (containsAny(normalized, "template", "템플릿")) {
            return "TEMPLATE";
        }
        return "CODING";
    }

    private String extractor(String fileName) {
        return switch (extension(fileName)) {
            case ".xml" -> SourceOntology.XML_EXTRACTOR;
            case ".yml", ".yaml" -> SourceOntology.YAML_EXTRACTOR;
            default -> SourceOntology.DOCUMENT_EXTRACTOR;
        };
    }

    private String extension(String fileName) {
        String value = firstText(fileName, "").toLowerCase(Locale.ROOT);
        int index = value.lastIndexOf('.');
        return index < 0 ? "" : value.substring(index);
    }

    private String cleanIdentifier(String value) {
        return value == null ? "" : value.replace("$", "").trim();
    }

    private String simpleName(String fqn) {
        int index = fqn.lastIndexOf('.');
        return index < 0 ? fqn : fqn.substring(index + 1);
    }

    private String stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String abbreviate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 297) + "...";
    }

    private String unquote(String value) {
        String result = firstText(value, "");
        if (result.length() >= 2) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            if ((first == 34 || first == 39) && first == last) {
                return result.substring(1, result.length() - 1);
            }
        }
        return result;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private final class Graph {
        private final Context context;
        private final Map<String, SourceGraphNodeResponse> nodes = new LinkedHashMap<>();
        private final Set<RelationshipKey> relationships = new LinkedHashSet<>();

        private Graph(Context context) {
            this.context = context;
        }

        private void node(String uid, String label, String name, Map<String, Object> specific) {
            Map<String, Object> properties = new LinkedHashMap<>(metadata());
            properties.put("uid", uid);
            properties.put("name", name);
            properties.putAll(specific);
            nodes.merge(uid, new SourceGraphNodeResponse(uid, label, name, properties), (left, right) -> {
                Map<String, Object> merged = new LinkedHashMap<>(left.properties());
                merged.putAll(right.properties());
                return new SourceGraphNodeResponse(uid, left.label(), right.name(), merged);
            });
        }

        private void relationship(String source, String target, String type) {
            relationships.add(new RelationshipKey(source, target, type));
        }

        private Map<String, Object> metadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("graphKey", context.graphKey());
            metadata.put("projectId", context.projectId());
            metadata.put("sourceKey", context.source());
            metadata.put("fileHash", firstText(context.fileHash(), ""));
            metadata.put("analysisVersion", SourceOntology.ANALYSIS_VERSION);
            metadata.put("validFrom", context.indexedAt());
            metadata.put("validTo", "");
            metadata.put("confidence", 0.9d);
            metadata.put("extractor", context.extractor());
            metadata.put("evidenceChunkIds", context.chunkIds());
            metadata.put("createdAt", context.indexedAt());
            return metadata;
        }

        private SourceGraphResponse response() {
            List<SourceGraphRelationshipResponse> responseRelationships = relationships.stream()
                    .map(key -> new SourceGraphRelationshipResponse(
                            key.source(), key.target(), key.type(), metadata()
                    )).toList();
            return new SourceGraphResponse(null, List.copyOf(nodes.values()), responseRelationships);
        }
    }

    private record Context(
            String graphKey, String projectId, String moduleName, String fileName, String filePath,
            String fileHash, String source, String documentUid, String content, List<String> chunkIds,
            String indexedAt, String extractor
    ) {
    }

    private record RelationshipKey(String source, String target, String type) {
    }

    private record YamlLevel(int indent, String key) {
    }

    private record YamlProperty(String key, String value) {
    }
}
