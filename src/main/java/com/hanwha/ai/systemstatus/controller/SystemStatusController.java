package com.hanwha.ai.systemstatus.controller;

import com.hanwha.ai.systemstatus.dto.SystemStatusResponse;
import com.hanwha.ai.systemstatus.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-status")
public class SystemStatusController {
    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping
    public SystemStatusResponse status() {
        return systemStatusService.getSnapshot();
    }

    @PostMapping("/check")
    public SystemStatusResponse check() {
        return systemStatusService.checkNow();
    }
}
