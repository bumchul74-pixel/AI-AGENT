package com.hanwha.ai.sourcequality.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanwha.ai.sourcequality.dto.SourceQualityDashboardResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityDuplicateGroupDetailResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityMethodDetailResponse;
import com.hanwha.ai.sourcequality.service.SourceQualityService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SourceQualityControllerTest {
    private final SourceQualityService service = mock(SourceQualityService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SourceQualityController(service)).build();
        SourceQualityDashboardResponse response = new SourceQualityDashboardResponse(
                "commerce", LocalDateTime.of(2026, 8, 12, 10, 0),
                null, null, null, List.of(), List.of(), List.of());
        when(service.get("commerce")).thenReturn(response);
        when(service.evaluate("commerce")).thenReturn(response);
        when(service.updateThresholds(any(), any())).thenReturn(response);
        when(service.getMethodDetail("commerce", "method-1")).thenReturn(
                new SourceQualityMethodDetailResponse(
                        "method-1", "com.example.Sample", "run()", "src/Sample.java",
                        10, 20, 11, 12, 25, 4, 0, 1, 0, 9, 3, "{ run(); }"));
        when(service.getDuplicateGroup("commerce", "EXACT", "hash")).thenReturn(
                new SourceQualityDuplicateGroupDetailResponse("EXACT", "hash", 2, List.of()));
    }

    @Test
    void exposesReadEvaluateAndThresholdContracts() throws Exception {
        mockMvc.perform(get("/api/source-quality/projects/commerce"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectKey").value("commerce"));
        mockMvc.perform(get("/api/source-quality/projects/commerce/methods/detail")
                        .queryParam("methodUid", "method-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodUid").value("method-1"))
                .andExpect(jsonPath("$.cyclomaticComplexity").value(12))
                .andExpect(jsonPath("$.methodBody").value("{ run(); }"));
        mockMvc.perform(get("/api/source-quality/projects/commerce/duplicate-groups/EXACT/hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXACT"))
                .andExpect(jsonPath("$.methodCount").value(2));
        mockMvc.perform(post("/api/source-quality/projects/commerce/evaluate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectKey").value("commerce"));
        mockMvc.perform(put("/api/source-quality/projects/commerce/thresholds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cyclomaticComplexity":10,"cognitiveComplexity":15,
                                 "duplicateRatio":10,"minimumDuplicateLines":5}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectKey").value("commerce"));

        verify(service).get("commerce");
        verify(service).getDuplicateGroup("commerce", "EXACT", "hash");
        verify(service).getMethodDetail("commerce", "method-1");
        verify(service).evaluate("commerce");
        verify(service).updateThresholds(any(), any());
    }
}
