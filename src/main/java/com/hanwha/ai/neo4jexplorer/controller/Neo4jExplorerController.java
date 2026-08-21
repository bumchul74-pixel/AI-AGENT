package com.hanwha.ai.neo4jexplorer.controller;

import com.hanwha.ai.neo4jexplorer.dto.Neo4jLabelGraphResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeDetailResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodePageResponse;
import com.hanwha.ai.neo4jexplorer.service.Neo4jExplorerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/neo4j-explorer")
public class Neo4jExplorerController {
    private final Neo4jExplorerService service;

    public Neo4jExplorerController(Neo4jExplorerService service) {
        this.service = service;
    }

    @GetMapping("/nodes")
    public Neo4jNodePageResponse nodes(
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return service.findNodes(label, keyword, page, size);
    }

    @GetMapping("/schema")
    public Neo4jLabelGraphResponse schema() {
        return service.findLabelGraph();
    }

    @GetMapping("/nodes/{elementId}")
    public ResponseEntity<Neo4jNodeDetailResponse> detail(@PathVariable String elementId) {
        return service.findDetail(elementId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}