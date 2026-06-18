package com.hanwha.ai.rag.controller;

import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.RagStatsResponse;
import com.hanwha.ai.rag.service.RagClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagClient ragClient;

    public RagController(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    @PostMapping("/search")
    public RagSearchResponse search(@RequestBody RagSearchRequest request) {
        return ragClient.search(request);
    }

    @GetMapping("/stats")
    public RagStatsResponse stats() {
        return ragClient.stats();
    }
}
