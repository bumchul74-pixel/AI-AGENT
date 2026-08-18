package com.hanwha.ai.mcp.controller;

import com.hanwha.ai.mcp.config.AgentConfigurationAdminAccessGuard;
import com.hanwha.ai.mcp.config.AgentConfigurationService;
import com.hanwha.ai.mcp.config.AgentConfigurationView;
import com.hanwha.ai.mcp.dto.AgentConfigurationUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agent-configurations")
public class AgentConfigurationAdminController {
    private final AgentConfigurationAdminAccessGuard accessGuard;
    private final AgentConfigurationService configurationService;

    public AgentConfigurationAdminController(
            AgentConfigurationAdminAccessGuard accessGuard,
            AgentConfigurationService configurationService
    ) {
        this.accessGuard = accessGuard;
        this.configurationService = configurationService;
    }

    @GetMapping("/active")
    public AgentConfigurationView active() {
        accessGuard.requireEnabled();
        return configurationService.active();
    }

    @PutMapping("/active")
    public AgentConfigurationView saveAndActivate(
            @RequestBody AgentConfigurationUpdateRequest request
    ) {
        accessGuard.requireEnabled();
        return configurationService.saveAndActivate(
                request == null ? null : request.configuration(),
                "admin-ui"
        );
    }

    @PostMapping("/refresh")
    public AgentConfigurationView refresh() {
        accessGuard.requireEnabled();
        return configurationService.refresh();
    }
}
