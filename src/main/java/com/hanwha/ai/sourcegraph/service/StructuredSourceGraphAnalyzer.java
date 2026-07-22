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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Extracts normalized ontology entities from non-Java source documents. */
public class StructuredSourceGraphAnalyzer {
    private static final int MAX_SQL_VARIANTS = 32;
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

        for (MyBatisStatementAnalysis statement : mapperStatements(context, mapper)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("statementId", statement.statementId());
            properties.put("namespace", statement.namespace());
            properties.put("operation", statement.operation());
            properties.put("commandType", statement.commandType());
            properties.put("sql", statement.sqlTemplate());
            properties.put("sqlVariants", statement.sqlVariants());
            properties.put("dynamic", statement.dynamic());
            properties.put("conditions", statement.conditions());
            properties.put("parameterType", statement.parameterType());
            properties.put("resultType", statement.resultType());
            properties.put("sourceFormat", "mybatis-xml");
            graph.node(statement.statementUid(), "SqlStatement", statement.statementId(), properties);
            graph.relationship(mapperUid, statement.statementUid(), "HAS_STATEMENT");
            String statementChunkId = statementChunkId(context.source(), statement.statementId());
            if (context.chunkIds().contains(statementChunkId)) {
                graph.relationship(statementChunkId, statement.statementUid(), "DESCRIBES");
                graph.relationship(statementChunkId, statement.statementUid(), "EVIDENCE_FOR");
            }
            for (String sqlVariant : statement.sqlVariants()) {
                addSqlEntities(context, graph, statement.statementUid(), mapperUid,
                        sqlVariant, statement.operation());
            }
        }
    }

    public List<MyBatisStatementAnalysis> analyzeMyBatisStatements(JavaSourceGraphIngestRequest request) {
        Context context = context(request);
        if (!".xml".equals(extension(context.fileName()))) {
            return List.of();
        }
        Element mapper = xmlRoot(context.content());
        if (mapper == null || !"mapper".equals(mapper.getTagName())) {
            return List.of();
        }
        return mapperStatements(context, mapper);
    }

    public static String statementChunkId(String sourceKey, String statementId) {
        return sourceKey + ":statement:" + statementId;
    }

    private List<MyBatisStatementAnalysis> mapperStatements(Context context, Element mapper) {
        String namespace = mapper.getAttribute("namespace").trim();
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException("MyBatis mapper namespace is required.");
        }
        Map<String, Element> fragments = sqlFragments(mapper, namespace);
        List<MyBatisStatementAnalysis> result = new ArrayList<>();
        for (String tag : List.of("select", "insert", "update", "delete")) {
            NodeList statements = mapper.getElementsByTagName(tag);
            for (int index = 0; index < statements.getLength(); index++) {
                Element statement = (Element) statements.item(index);
                String statementId = statement.getAttribute("id").trim();
                if (!StringUtils.hasText(statementId)) {
                    continue;
                }
                DynamicSql rendered = renderChildren(statement, fragments, namespace, new LinkedHashSet<>());
                List<String> variants = rendered.variants().stream()
                        .map(this::normalizeSql)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .limit(MAX_SQL_VARIANTS)
                        .toList();
                String template = normalizeSql(rendered.template());
                if (!StringUtils.hasText(template) && !variants.isEmpty()) {
                    template = variants.get(0);
                }
                result.add(new MyBatisStatementAnalysis(
                        statementId,
                        namespace,
                        SourceGraphIdentity.sqlStatementUid(context.projectId(), namespace, statementId),
                        tag.toUpperCase(Locale.ROOT),
                        "select".equals(tag) ? "READ" : "WRITE",
                        template,
                        variants.isEmpty() ? List.of(template) : variants,
                        rendered.dynamic(),
                        List.copyOf(rendered.conditions()),
                        statement.getAttribute("parameterType").trim(),
                        firstText(statement.getAttribute("resultType"), statement.getAttribute("resultMap"))
                ));
            }
        }
        return List.copyOf(result);
    }

    private Map<String, Element> sqlFragments(Element mapper, String namespace) {
        Map<String, Element> fragments = new LinkedHashMap<>();
        NodeList nodes = mapper.getElementsByTagName("sql");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element fragment = (Element) nodes.item(index);
            String id = fragment.getAttribute("id").trim();
            if (StringUtils.hasText(id)) {
                fragments.put(id, fragment);
                fragments.put(namespace + "." + id, fragment);
            }
        }
        return fragments;
    }

    private DynamicSql renderChildren(Element parent, Map<String, Element> fragments,
                                      String namespace, Set<String> includeStack) {
        DynamicSql result = DynamicSql.empty();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            result = concatenate(result, renderNode(children.item(index), fragments, namespace, includeStack));
        }
        return result;
    }

    private DynamicSql renderNode(Node node, Map<String, Element> fragments,
                                  String namespace, Set<String> includeStack) {
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String text = node.getTextContent();
            return new DynamicSql(text, List.of(text), new LinkedHashSet<>(), false);
        }
        if (!(node instanceof Element element)) {
            return DynamicSql.empty();
        }
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        if ("include".equals(tag)) {
            return renderInclude(element, fragments, namespace, includeStack);
        }
        if ("if".equals(tag)) {
            DynamicSql child = renderChildren(element, fragments, namespace, includeStack);
            String test = element.getAttribute("test").trim();
            LinkedHashSet<String> conditions = new LinkedHashSet<>(child.conditions());
            if (StringUtils.hasText(test)) {
                conditions.add(test);
            }
            List<String> variants = new ArrayList<>();
            variants.add("");
            variants.addAll(child.variants());
            return new DynamicSql(
                    " /* IF " + test + " */ " + child.template() + " /* END IF */ ",
                    limitedDistinct(variants), conditions, true
            );
        }
        if ("choose".equals(tag)) {
            return renderChoose(element, fragments, namespace, includeStack);
        }
        DynamicSql child = renderChildren(element, fragments, namespace, includeStack);
        return switch (tag) {
            case "where" -> transform(child, value -> prefixSql("WHERE", stripPrefix(value, "AND", "OR")), true);
            case "set" -> transform(child, value -> prefixSql("SET", stripSuffix(value, ",")), true);
            case "trim" -> renderTrim(element, child);
            case "foreach" -> renderForeach(element, child);
            case "bind" -> {
                LinkedHashSet<String> conditions = new LinkedHashSet<>(child.conditions());
                conditions.add("bind:" + element.getAttribute("name") + "=" + element.getAttribute("value"));
                yield new DynamicSql(child.template(), child.variants(), conditions, true);
            }
            default -> child;
        };
    }

    private DynamicSql renderInclude(Element include, Map<String, Element> fragments,
                                     String namespace, Set<String> includeStack) {
        String refid = include.getAttribute("refid").trim();
        String resolvedRefid = refid.contains(".") ? refid : namespace + "." + refid;
        Element fragment = fragments.getOrDefault(resolvedRefid, fragments.get(refid));
        if (fragment == null) {
            return new DynamicSql(" /* UNRESOLVED INCLUDE " + refid + " */ ",
                    List.of(""), new LinkedHashSet<>(List.of("unresolved-include:" + refid)), true);
        }
        if (!includeStack.add(resolvedRefid)) {
            return new DynamicSql(" /* CYCLIC INCLUDE " + refid + " */ ",
                    List.of(""), new LinkedHashSet<>(List.of("cyclic-include:" + refid)), true);
        }
        try {
            DynamicSql rendered = renderChildren(fragment, fragments, namespace, includeStack);
            Map<String, String> variables = new LinkedHashMap<>();
            NodeList properties = include.getElementsByTagName("property");
            for (int index = 0; index < properties.getLength(); index++) {
                Element property = (Element) properties.item(index);
                variables.put(property.getAttribute("name"), property.getAttribute("value"));
            }
            return replaceVariables(rendered, variables);
        } finally {
            includeStack.remove(resolvedRefid);
        }
    }

    private DynamicSql renderChoose(Element choose, Map<String, Element> fragments,
                                    String namespace, Set<String> includeStack) {
        List<String> templates = new ArrayList<>();
        List<String> variants = new ArrayList<>();
        LinkedHashSet<String> conditions = new LinkedHashSet<>();
        NodeList children = choose.getChildNodes();
        boolean hasOtherwise = false;
        for (int index = 0; index < children.getLength(); index++) {
            if (!(children.item(index) instanceof Element branch)) {
                continue;
            }
            String tag = branch.getTagName().toLowerCase(Locale.ROOT);
            if (!"when".equals(tag) && !"otherwise".equals(tag)) {
                continue;
            }
            DynamicSql rendered = renderChildren(branch, fragments, namespace, includeStack);
            variants.addAll(rendered.variants());
            conditions.addAll(rendered.conditions());
            String test = "when".equals(tag) ? branch.getAttribute("test").trim() : "otherwise";
            conditions.add(test);
            templates.add("/* " + tag.toUpperCase(Locale.ROOT) + " " + test + " */ " + rendered.template());
            hasOtherwise |= "otherwise".equals(tag);
        }
        if (!hasOtherwise) {
            variants.add("");
        }
        return new DynamicSql(String.join(" /* OR */ ", templates), limitedDistinct(variants), conditions, true);
    }

    private DynamicSql renderForeach(Element element, DynamicSql child) {
        String open = element.getAttribute("open");
        String close = element.getAttribute("close");
        String separator = element.getAttribute("separator");
        String collection = element.getAttribute("collection");
        String item = element.getAttribute("item");
        List<String> variants = child.variants().stream()
                .map(value -> open + value + close)
                .toList();
        LinkedHashSet<String> conditions = new LinkedHashSet<>(child.conditions());
        conditions.add("foreach:" + collection + ":" + item + ":separator=" + separator);
        return new DynamicSql(open + " /* FOREACH " + collection + " AS " + item + " */ "
                + child.template() + " " + close, limitedDistinct(variants), conditions, true);
    }

    private DynamicSql renderTrim(Element element, DynamicSql child) {
        String prefix = element.getAttribute("prefix");
        String suffix = element.getAttribute("suffix");
        String[] prefixOverrides = element.getAttribute("prefixOverrides").split("\\|");
        String[] suffixOverrides = element.getAttribute("suffixOverrides").split("\\|");
        return transform(child, value -> prefixSql(prefix,
                stripSuffix(stripPrefix(value, prefixOverrides), suffixOverrides)) + suffix, true);
    }

    private DynamicSql replaceVariables(DynamicSql sql, Map<String, String> variables) {
        if (variables.isEmpty()) {
            return sql;
        }
        java.util.function.UnaryOperator<String> replace = value -> {
            String result = value;
            for (Map.Entry<String, String> variable : variables.entrySet()) {
                result = result.replace("${" + variable.getKey() + "}", variable.getValue());
            }
            return result;
        };
        return new DynamicSql(replace.apply(sql.template()),
                sql.variants().stream().map(replace).toList(), sql.conditions(), sql.dynamic());
    }

    private DynamicSql transform(DynamicSql sql, java.util.function.UnaryOperator<String> transform,
                                 boolean dynamic) {
        return new DynamicSql(transform.apply(sql.template()),
                limitedDistinct(sql.variants().stream().map(transform).toList()),
                sql.conditions(), sql.dynamic() || dynamic);
    }

    private DynamicSql concatenate(DynamicSql left, DynamicSql right) {
        List<String> variants = new ArrayList<>();
        for (String leftVariant : left.variants()) {
            for (String rightVariant : right.variants()) {
                variants.add(leftVariant + " " + rightVariant);
                if (variants.size() >= MAX_SQL_VARIANTS) {
                    break;
                }
            }
            if (variants.size() >= MAX_SQL_VARIANTS) {
                break;
            }
        }
        LinkedHashSet<String> conditions = new LinkedHashSet<>(left.conditions());
        conditions.addAll(right.conditions());
        return new DynamicSql(left.template() + " " + right.template(),
                limitedDistinct(variants), conditions, left.dynamic() || right.dynamic());
    }

    private List<String> limitedDistinct(List<String> values) {
        return values.stream().distinct().limit(MAX_SQL_VARIANTS).toList();
    }

    private String prefixSql(String prefix, String value) {
        return StringUtils.hasText(value) && StringUtils.hasText(prefix) ? prefix + " " + value : value;
    }

    private String stripPrefix(String value, String... overrides) {
        String result = value == null ? "" : value.stripLeading();
        for (String override : overrides) {
            String candidate = override.trim();
            if (StringUtils.hasText(candidate) && result.toUpperCase(Locale.ROOT)
                    .startsWith(candidate.toUpperCase(Locale.ROOT))) {
                return result.substring(candidate.length()).stripLeading();
            }
        }
        return result;
    }

    private String stripSuffix(String value, String... overrides) {
        String result = value == null ? "" : value.stripTrailing();
        for (String override : overrides) {
            String candidate = override.trim();
            if (StringUtils.hasText(candidate) && result.toUpperCase(Locale.ROOT)
                    .endsWith(candidate.toUpperCase(Locale.ROOT))) {
                return result.substring(0, result.length() - candidate.length()).stripTrailing();
            }
        }
        return result;
    }

    private String normalizeSql(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
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

    private record DynamicSql(
            String template,
            List<String> variants,
            LinkedHashSet<String> conditions,
            boolean dynamic
    ) {
        private static DynamicSql empty() {
            return new DynamicSql("", List.of(""), new LinkedHashSet<>(), false);
        }
    }

    public record MyBatisStatementAnalysis(
            String statementId,
            String namespace,
            String statementUid,
            String commandType,
            String operation,
            String sqlTemplate,
            List<String> sqlVariants,
            boolean dynamic,
            List<String> conditions,
            String parameterType,
            String resultType
    ) {
    }
}
