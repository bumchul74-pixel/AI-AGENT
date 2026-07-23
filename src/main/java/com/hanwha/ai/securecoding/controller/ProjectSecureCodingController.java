package com.hanwha.ai.securecoding.controller;

import com.hanwha.ai.securecoding.dto.SecureCodingScanJobResponse;
import com.hanwha.ai.securecoding.dto.SecureCodingExportRequest;
import com.hanwha.ai.securecoding.service.SecureCodingExcelService;
import com.hanwha.ai.securecoding.service.SecureCodingScanJobService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secure-coding")
public class ProjectSecureCodingController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SecureCodingExcelService excelService;
    private final SecureCodingScanJobService jobService;

    public ProjectSecureCodingController(
            SecureCodingExcelService excelService, SecureCodingScanJobService jobService) {
        this.excelService = excelService;
        this.jobService = jobService;
    }

    @PostMapping("/projects/{projectKey}/scan")
    public ResponseEntity<SecureCodingScanJobResponse> scan(@PathVariable String projectKey) {
        return ResponseEntity.accepted().body(jobService.start(projectKey));
    }

    @GetMapping("/scans/{jobId}")
    public SecureCodingScanJobResponse scanStatus(@PathVariable Long jobId) {
        return jobService.get(jobId);
    }

    @GetMapping("/projects/{projectKey}/scans/latest")
    public ResponseEntity<SecureCodingScanJobResponse> latest(@PathVariable String projectKey) {
        SecureCodingScanJobResponse response = jobService.latest(projectKey);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody SecureCodingExportRequest request) {
        String projectKey = request == null || request.projectKey() == null
                ? "project" : request.projectKey().replaceAll("[^a-zA-Z0-9_-]", "_");
        byte[] content = excelService.export(request == null ? null : request.rows());
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("secure-coding-" + projectKey + ".xlsx", StandardCharsets.UTF_8)
                        .build().toString())
                .body(content);
    }
}
