package com.hanwha.ai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String baseUrl,
        String searchPath,
        int topK,
        int hybridGraphDepth,
        int hybridMaxGraphNodes,
        int hybridMaxGraphRelationships,
        int hybridMaxEvidenceChunks,
        int maxContextCharacters
) {
    public static final int DEFAULT_HYBRID_GRAPH_DEPTH = 2;
    public static final int DEFAULT_HYBRID_MAX_GRAPH_NODES = 50;
    public static final int DEFAULT_HYBRID_MAX_GRAPH_RELATIONSHIPS = 200;
    public static final int DEFAULT_HYBRID_MAX_EVIDENCE_CHUNKS = 12;
    public static final int DEFAULT_MAX_CONTEXT_CHARACTERS = 60_000;

    @ConstructorBinding
    public RagProperties {
        hybridGraphDepth = positiveOrDefault(hybridGraphDepth, DEFAULT_HYBRID_GRAPH_DEPTH);
        hybridMaxGraphNodes = positiveOrDefault(hybridMaxGraphNodes, DEFAULT_HYBRID_MAX_GRAPH_NODES);
        hybridMaxGraphRelationships = positiveOrDefault(
                hybridMaxGraphRelationships, DEFAULT_HYBRID_MAX_GRAPH_RELATIONSHIPS);
        hybridMaxEvidenceChunks = positiveOrDefault(
                hybridMaxEvidenceChunks, DEFAULT_HYBRID_MAX_EVIDENCE_CHUNKS);
        maxContextCharacters = positiveOrDefault(maxContextCharacters, DEFAULT_MAX_CONTEXT_CHARACTERS);
    }

    public RagProperties(String baseUrl, String searchPath, int topK) {
        this(baseUrl, searchPath, topK, 0, 0, 0, 0, 0);
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
