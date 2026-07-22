package com.hanwha.ai.sourcegraph.domain;

import java.util.Set;

public final class SourceOntology {
    public static final String ANALYSIS_VERSION = "1.1";
    public static final String JAVA_EXTRACTOR = "JavaParser";
    public static final String XML_EXTRACTOR = "MyBatisXmlAnalyzer";
    public static final String YAML_EXTRACTOR = "YamlConfigurationAnalyzer";
    public static final String DOCUMENT_EXTRACTOR = "StandardDocumentAnalyzer";

    public static final Set<String> NODE_LABELS = Set.of(
            "Project", "Module", "Package", "SourceFile", "JavaType", "Method", "Field",
            "ApiEndpoint", "Service", "Repository", "DTO", "Domain", "Mapper",
            "DatabaseTable", "DatabaseColumn", "SqlStatement", "Document", "Chunk",
            "StandardRule", "StandardTemplate", "Generation", "ConfigurationFile",
            "ConfigurationProperty", "Bean", "Profile", "Provider"
    );

    public static final Set<String> JAVA_TYPE_CLASSIFICATIONS = Set.of(
            "Service", "Repository", "DTO", "Domain", "Mapper"
    );

    public static final Set<String> RELATIONSHIP_TYPES = Set.of(
            "CONTAINS", "DECLARES", "HAS_METHOD", "HAS_FIELD", "IMPORTS", "EXTENDS",
            "IMPLEMENTS", "INJECTS", "USES", "CALLS", "HANDLED_BY", "READS_FROM",
            "WRITES_TO", "MAPS_TO", "USES_DTO", "CONFORMS_TO", "VIOLATES",
            "DESCRIBES", "EVIDENCE_FOR", "BASED_ON", "HAS_COLUMN", "EXECUTES",
            "HAS_SOURCE", "GENERATED", "INGESTED", "HAS_MAPPER_XML", "HAS_STATEMENT",
            "REFERENCES_COLUMN", "DEFINES", "CONFIGURES", "ACTIVATES"
    );

    private SourceOntology() {
    }
}
