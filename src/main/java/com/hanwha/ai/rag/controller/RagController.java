package com.hanwha.ai.rag.controller;

import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.RagStatsResponse;
import com.hanwha.ai.rag.service.RagClient;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        int vectorJavaFileCount = ragClient.stats().javaFileCount();
        int graphJavaFileCount = sourceGraphService.countJavaSourceFiles();
        return new RagStatsResponse(vectorJavaFileCount + graphJavaFileCount);
    }
}
