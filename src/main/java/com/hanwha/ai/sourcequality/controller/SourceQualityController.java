package com.hanwha.ai.sourcequality.controller;

import com.hanwha.ai.sourcequality.dto.SourceQualityDashboardResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityDuplicateGroupDetailResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityMethodDetailResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityThresholdRequest;
import com.hanwha.ai.sourcequality.service.SourceQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/source-quality/projects/{projectKey}")
public class SourceQualityController {
    private final SourceQualityService service;

    public SourceQualityController(SourceQualityService service) { this.service = service; }

    @GetMapping
    public SourceQualityDashboardResponse get(@PathVariable String projectKey) {
        return service.get(projectKey);
    }

    @GetMapping("/methods/detail")
    public SourceQualityMethodDetailResponse getMethodDetail(
            @PathVariable String projectKey,
            @RequestParam String methodUid) {
        return service.getMethodDetail(projectKey, methodUid);
    }

    @GetMapping("/duplicate-groups/{type}/{hash}")
    public SourceQualityDuplicateGroupDetailResponse getDuplicateGroup(
            @PathVariable String projectKey,
            @PathVariable String type,
            @PathVariable String hash) {
        return service.getDuplicateGroup(projectKey, type, hash);
    }

    @PostMapping("/evaluate")
    public SourceQualityDashboardResponse evaluate(@PathVariable String projectKey) {
        return service.evaluate(projectKey);
    }

    @PutMapping("/thresholds")
    public SourceQualityDashboardResponse updateThresholds(@PathVariable String projectKey,
                                                            @RequestBody SourceQualityThresholdRequest request) {
        return service.updateThresholds(projectKey, request);
    }
}
