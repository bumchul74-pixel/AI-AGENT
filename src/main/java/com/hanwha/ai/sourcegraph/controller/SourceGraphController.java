package com.hanwha.ai.sourcegraph.controller;

import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeSourceResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/source-graph")
public class SourceGraphController {
    private final SourceGraphService sourceGraphService;

    public SourceGraphController(SourceGraphService sourceGraphService) {
        this.sourceGraphService = sourceGraphService;
    }

    @PostMapping("/java-files")
    public SourceGraphIndexResult indexJavaFile(@RequestBody JavaSourceGraphIngestRequest request) {
        return sourceGraphService.indexJavaSource(request);
    }

    @GetMapping
    public SourceGraphResponse overview(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String projectKey,
            @RequestParam(defaultValue = "600") int limit
    ) {
        return sourceGraphService.findOverview(query, limit, projectKey);
    }


    @GetMapping("/node-source")
    public SourceGraphNodeSourceResponse nodeSource(@RequestParam String nodeId) {
        return sourceGraphService.findNodeSource(nodeId);
    }

    @GetMapping("/dependencies")
    public SourceGraphResponse dependencies(@RequestParam String fqn) {
        return sourceGraphService.findDependencies(fqn);
    }

    @GetMapping("/impacts")
    public SourceGraphResponse impacts(@RequestParam String fqn) {
        return sourceGraphService.findImpacts(fqn);
    }

    @PostMapping("/neighborhood")
    public SourceGraphResponse neighborhood(
            @RequestBody List<String> entityIds,
            @RequestParam(defaultValue = "3") int depth
    ) {
        return sourceGraphService.findNeighborhoodByEntityIds(entityIds, depth);
    }
}
