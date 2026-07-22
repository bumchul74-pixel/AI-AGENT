package com.hanwha.ai.rag.controller;

import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.RagStatsResponse;
import com.hanwha.ai.rag.dto.RagSourceResponse;
import com.hanwha.ai.rag.dto.RagSourcePageResponse;
import com.hanwha.ai.rag.service.RagClient;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagClient ragClient;
    private final SourceGraphService sourceGraphService;

    public RagController(RagClient ragClient, SourceGraphService sourceGraphService) {
        this.ragClient = ragClient;
        this.sourceGraphService = sourceGraphService;
    }

    @PostMapping("/search")
    public RagSearchResponse search(@RequestBody RagSearchRequest request) {
        return ragClient.search(request);
    }

    @GetMapping("/stats")
    public RagStatsResponse stats() {
        return ragClient.stats();
    }

    @GetMapping("/sources")
    public RagSourcePageResponse sources(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String projectKey
    ) {
        Map<String, SourceInventory> sources = new LinkedHashMap<>();
        for (RagSourceResponse source : ragClient.findSources()) {
            SourceInventory inventory = sources.computeIfAbsent(source.sourceKey(), SourceInventory::new);
            inventory.documentId = source.documentId();
            inventory.projectId = source.projectId();
            inventory.filePath = source.filePath();
            inventory.fileHash = source.fileHash();
            inventory.chunkCount = source.chunkCount();
            inventory.vectorTracked = true;
            inventory.fileName = source.fileName();
        }

        sourceGraphService.findIndexedSources().forEach(source -> {
            SourceInventory inventory = sources.computeIfAbsent(source.sourceKey(), SourceInventory::new);
            inventory.graphTracked = true;
            inventory.graphKey = source.graphKey();
            inventory.graphNodeCount = source.nodeCount();
            if (!StringUtils.hasText(inventory.projectId)) {
                inventory.projectId = source.projectId();
            }
            if (!StringUtils.hasText(inventory.fileName)) {
                inventory.fileName = source.fileName();
            }
        });
        List<RagSourceResponse> allSources = sources.values().stream()
                .map(SourceInventory::response)
                .filter(source -> !StringUtils.hasText(projectKey) || projectKey.equals(source.projectId()))
                .sorted((left, right) -> left.fileName().compareToIgnoreCase(right.fileName()))
                .toList();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int fromIndex = Math.min(safePage * safeSize, allSources.size());
        int toIndex = Math.min(fromIndex + safeSize, allSources.size());
        return new RagSourcePageResponse(
                allSources.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                allSources.size(),
                toIndex < allSources.size()
        );
    }

    @DeleteMapping("/sources")
    public void deleteSource(
            @RequestParam String sourceKey,
            @RequestParam(required = false) String graphKey
    ) {
        ragClient.deleteSource(sourceKey);
        if (StringUtils.hasText(graphKey)) {
            sourceGraphService.deleteByGraphKey(graphKey);
        } else {
            sourceGraphService.deleteBySourceKey(sourceKey);
        }
    }

    private static final class SourceInventory {
        private final String sourceKey;
        private Long documentId;
        private String projectId = "";
        private String filePath = "";
        private String fileHash = "";
        private int chunkCount;
        private boolean vectorTracked;
        private boolean graphTracked;
        private String graphKey = "";
        private String fileName = "";
        private int graphNodeCount;

        private SourceInventory(String sourceKey) {
            this.sourceKey = sourceKey;
        }

        private RagSourceResponse response() {
            String resolvedFileName = StringUtils.hasText(fileName)
                    ? fileName
                    : (StringUtils.hasText(filePath) ? filePath : sourceKey);
            return new RagSourceResponse(sourceKey, documentId, projectId, filePath, fileHash,
                    chunkCount, vectorTracked, graphTracked, graphKey, resolvedFileName, graphNodeCount);
        }
    }
}
