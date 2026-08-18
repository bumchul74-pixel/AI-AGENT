package com.hanwha.ai.neo4jexplorer.service;

import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeDetailResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodePageResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeSummary;
import com.hanwha.ai.neo4jexplorer.repository.Neo4jExplorerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class Neo4jExplorerService {
    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;
    private final Neo4jExplorerRepository repository;

    public Neo4jExplorerService(Neo4jExplorerRepository repository) {
        this.repository = repository;
    }

    public Neo4jNodePageResponse findNodes(String label, String keyword, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        String safeLabel = normalize(label);
        String safeKeyword = normalize(keyword).toLowerCase(java.util.Locale.ROOT);
        long total = repository.count(safeLabel, safeKeyword);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        List<Neo4jNodeSummary> content = safePage >= totalPages && totalPages > 0
                ? List.of()
                : repository.findPage(safeLabel, safeKeyword, safePage * safeSize, safeSize);
        return new Neo4jNodePageResponse(
                content, safePage, safeSize, total, totalPages,
                safePage == 0, totalPages == 0 || safePage >= totalPages - 1
        );
    }

    public Optional<Neo4jNodeDetailResponse> findDetail(String elementId) {
        if (elementId == null || elementId.isBlank()) return Optional.empty();
        return repository.findDetail(elementId.trim());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}