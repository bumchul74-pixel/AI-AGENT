package com.hanwha.ai.mcp.controller;

import com.hanwha.ai.mcp.dto.AgentToolCatalogResponse;
import com.hanwha.ai.mcp.service.AgentToolCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/agent-tools")
public class AgentToolCatalogController {
    private final AgentToolCatalogService toolCatalogService;

    public AgentToolCatalogController(AgentToolCatalogService toolCatalogService) {
        this.toolCatalogService = toolCatalogService;
    }

    @GetMapping
    public AgentToolCatalogResponse activeTools() {
        return toolCatalogService.activeTools();
    }
}