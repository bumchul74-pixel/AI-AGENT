package com.hanwha.ai.securecoding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.securecoding.domain.SecureCodingScanFile;
import com.hanwha.ai.securecoding.domain.SecureCodingScanJob;
import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import com.hanwha.ai.securecoding.mapper.SecureCodingScanMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SecureCodingScanJobServiceTest {
    @Test
    void exposesMcpConnectionFailureAsTheJobStatusMessage() {
        SecureCodingScanMapper mapper = mock(SecureCodingScanMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        String message = "MCP 서버에 연결할 수 없습니다. MCP 서버 실행 상태와 연결 설정을 확인하세요.";
        SecureCodingScanJob job = new SecureCodingScanJob(
                9L, "sample", "COMPLETED_WITH_ERRORS", 1, 1, 0, 0, 1, null,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        SecureCodingResultRow error = new SecureCodingResultRow(
                10L, "UserService.java", "JAVA", "ERROR", "-", "-", message,
                null, null, null, null);
        when(mapper.findJobById(9L)).thenReturn(job);
        when(mapper.findResults(9L)).thenReturn(List.of(error));
        Executor direct = Runnable::run;
        SecureCodingScanJobService service = new SecureCodingScanJobService(
                mapper, mock(ProjectSecureCodingService.class), mock(RagDocumentRepository.class),
                direct, direct, transactionManager);

        assertThat(service.get(9L).message()).isEqualTo(message);
    }

    @Test
    void queuesFilesAndDoesNotCountPassedRowsAsFindings() {
        SecureCodingScanMapper mapper = mock(SecureCodingScanMapper.class);
        ProjectSecureCodingService scanner = mock(ProjectSecureCodingService.class);
        RagDocumentRepository documents = mock(RagDocumentRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        Executor direct = Runnable::run;

        RagDocument document = new RagDocument();
        document.setId(10L);
        document.setOriginalFileName("UserService.java");
        SecureCodingResultRow passed = new SecureCodingResultRow(
                10L, "UserService.java", "JAVA", "PASSED", "-", "-",
                "No security findings", null, null, null, null);
        SecureCodingScanJob completed = new SecureCodingScanJob(
                1L, "sample", "COMPLETED", 1, 1, 1, 0, 0, null,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(scanner.findScannableDocuments("sample")).thenReturn(List.of(document));
        when(scanner.fileType("UserService.java")).thenReturn("JAVA");
        when(mapper.insertJob("sample", 1)).thenReturn(1L);
        when(mapper.markJobRunning(1L)).thenReturn(1);
        when(mapper.findPendingFiles(1L)).thenReturn(List.of(
                new SecureCodingScanFile(2L, 1L, 10L, "UserService.java", "JAVA", "QUEUED", null)));
        when(mapper.markFileRunning(2L)).thenReturn(1);
        when(documents.findById(10L)).thenReturn(document);
        when(scanner.scanDocument(document)).thenReturn(
                new ProjectSecureCodingService.FileScanRows(List.of(passed), true, false));
        when(mapper.findJobById(1L)).thenReturn(completed);
        when(mapper.findResults(1L)).thenReturn(List.of(passed));

        SecureCodingScanJobService service = new SecureCodingScanJobService(
                mapper, scanner, documents, direct, direct, transactionManager);
        var response = service.start("sample");

        assertThat(response.jobId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(mapper).insertFile(1L, 10L, "UserService.java", "JAVA");
        verify(mapper).incrementProgress(1L, 1, 0, 0);
        verify(mapper).finalizeJob(1L);
    }
}
