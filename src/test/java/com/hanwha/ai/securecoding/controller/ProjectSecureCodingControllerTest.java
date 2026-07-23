package com.hanwha.ai.securecoding.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanwha.ai.securecoding.dto.SecureCodingScanJobResponse;
import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import com.hanwha.ai.securecoding.service.SecureCodingExcelService;
import com.hanwha.ai.securecoding.service.SecureCodingScanJobService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProjectSecureCodingControllerTest {

    @Test
    void scansProjectAndExportsCurrentGridRows() throws Exception {
        SecureCodingScanJobService scanService = mock(SecureCodingScanJobService.class);
        SecureCodingExcelService excelService = mock(SecureCodingExcelService.class);
        var row = new SecureCodingResultRow(
                1L, "UserService.java", "JAVA", "PASSED", "-", "-",
                "No security findings", null, null, null, null);
        when(scanService.start("sample")).thenReturn(new SecureCodingScanJobResponse(
                7L, "sample", "QUEUED", "점검 요청이 대기열에 등록되었습니다.", 0,
                1, 0, 0, 0, 0, LocalDateTime.of(2026, 7, 23, 12, 0),
                null, null, List.of()));
        when(excelService.export(anyList())).thenReturn(new byte[]{'P', 'K'});
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ProjectSecureCodingController(excelService, scanService)).build();

        mockMvc.perform(post("/api/secure-coding/projects/sample/scan"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.projectKey").value("sample"))
                .andExpect(jsonPath("$.jobId").value(7))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(post("/api/secure-coding/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectKey": "sample",
                                  "rows": [{
                                    "documentId": 1,
                                    "fileName": "UserService.java",
                                    "fileType": "JAVA",
                                    "status": "PASSED",
                                    "severity": "-",
                                    "ruleId": "-",
                                    "message": "No security findings"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("secure-coding-sample.xlsx")))
                .andExpect(content().bytes(new byte[]{'P', 'K'}));
    }
}
