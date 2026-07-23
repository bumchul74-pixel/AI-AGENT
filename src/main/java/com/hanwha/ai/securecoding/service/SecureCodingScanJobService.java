package com.hanwha.ai.securecoding.service;

import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.securecoding.domain.SecureCodingScanFile;
import com.hanwha.ai.securecoding.domain.SecureCodingScanJob;
import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import com.hanwha.ai.securecoding.dto.SecureCodingScanJobResponse;
import com.hanwha.ai.securecoding.mapper.SecureCodingScanMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.event.EventListener;

@Service
public class SecureCodingScanJobService {
    private static final Logger log = LoggerFactory.getLogger(SecureCodingScanJobService.class);
    private final SecureCodingScanMapper mapper;
    private final ProjectSecureCodingService scanner;
    private final RagDocumentRepository documents;
    private final Executor coordinator;
    private final Executor workers;
    private final TransactionTemplate transactions;

    public SecureCodingScanJobService(
            SecureCodingScanMapper mapper,
            ProjectSecureCodingService scanner,
            RagDocumentRepository documents,
            @Qualifier("secureCodingCoordinatorExecutor") Executor coordinator,
            @Qualifier("secureCodingFileExecutor") Executor workers,
            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.scanner = scanner;
        this.documents = documents;
        this.coordinator = coordinator;
        this.workers = workers;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public SecureCodingScanJobResponse start(String projectKey) {
        String normalized = projectKey == null ? "" : projectKey.trim();
        List<RagDocument> targets = scanner.findScannableDocuments(normalized);
        if (targets.isEmpty()) throw new BusinessException("No supported source files to scan.");
        SecureCodingScanJob active = mapper.findActiveByProject(normalized);
        if (active != null) return response(active, "이미 진행 중인 점검 작업을 표시합니다.");

        Long jobId;
        try {
            jobId = transactions.execute(status -> createJob(normalized, targets));
        } catch (DataAccessException exception) {
            active = mapper.findActiveByProject(normalized);
            if (active != null) return response(active, "이미 진행 중인 점검 작업을 표시합니다.");
            throw exception;
        }
        dispatch(jobId);
        return response(requiredJob(jobId), "점검 요청이 대기열에 등록되었습니다.");
    }

    public SecureCodingScanJobResponse get(Long jobId) {
        return response(requiredJob(jobId), null);
    }

    public SecureCodingScanJobResponse latest(String projectKey) {
        SecureCodingScanJob job = mapper.findLatestByProject(projectKey == null ? "" : projectKey.trim());
        return job == null ? null : response(job, null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        for (SecureCodingScanJob job : mapper.findRecoverableJobs()) {
            try {
                mapper.resetInterruptedFiles(job.id());
                dispatch(job.id());
            } catch (BusinessException exception) {
                log.warn("Could not recover secure coding scan job. jobId={}", job.id(), exception);
            }
        }
    }

    private Long createJob(String projectKey, List<RagDocument> targets) {
        Long jobId = mapper.insertJob(projectKey, targets.size());
        for (RagDocument document : targets) {
            mapper.insertFile(jobId, document.getId(), document.getOriginalFileName(),
                    scanner.fileType(document.getOriginalFileName()));
        }
        return jobId;
    }

    private void dispatch(Long jobId) {
        try {
            coordinator.execute(() -> runJob(jobId));
        } catch (TaskRejectedException exception) {
            mapper.failJob(jobId, "점검 대기열이 가득 찼습니다. 잠시 후 다시 실행해 주세요.");
            throw new BusinessException("점검 대기열이 가득 찼습니다. 잠시 후 다시 실행해 주세요.", exception);
        }
    }

    private void runJob(Long jobId) {
        try {
            if (mapper.markJobRunning(jobId) == 0) return;
            List<CompletableFuture<Void>> futures = mapper.findPendingFiles(jobId).stream()
                    .map(file -> CompletableFuture.runAsync(() -> processFile(file), workers))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            mapper.finalizeJob(jobId);
        } catch (RuntimeException exception) {
            log.error("Secure coding scan job failed. jobId={}", jobId, exception);
            mapper.failJob(jobId, rootMessage(exception));
        }
    }

    private void processFile(SecureCodingScanFile file) {
        if (mapper.markFileRunning(file.id()) == 0) return;
        RagDocument document = documents.findById(file.documentId());
        ProjectSecureCodingService.FileScanRows scan = document == null
                ? missingDocument(file) : scanner.scanDocument(document);
        transactions.executeWithoutResult(status -> saveFileResult(file, scan));
    }

    private void saveFileResult(SecureCodingScanFile file, ProjectSecureCodingService.FileScanRows scan) {
        scan.rows().forEach(row -> mapper.insertResult(file.jobId(), file.id(), row));
        String status = scan.error() ? "ERROR" : scan.passed() ? "PASSED" : "FINDING";
        String error = scan.error() && !scan.rows().isEmpty() ? scan.rows().getFirst().message() : null;
        mapper.completeFile(file.id(), status, error);
        int findings = scan.passed() || scan.error() ? 0 : scan.rows().size();
        mapper.incrementProgress(file.jobId(), scan.passed() ? 1 : 0,
                findings, scan.error() ? 1 : 0);
    }

    private ProjectSecureCodingService.FileScanRows missingDocument(SecureCodingScanFile file) {
        SecureCodingResultRow row = new SecureCodingResultRow(
                file.documentId(), file.fileName(), file.fileType(), "ERROR", "-", "-",
                "Source document no longer exists.", null, null, null, null);
        return new ProjectSecureCodingService.FileScanRows(List.of(row), false, true);
    }

    private SecureCodingScanJob requiredJob(Long jobId) {
        SecureCodingScanJob job = jobId == null ? null : mapper.findJobById(jobId);
        if (job == null) throw new BusinessException("Secure coding scan job does not exist.");
        return job;
    }

    private SecureCodingScanJobResponse response(SecureCodingScanJob job, String messageOverride) {
        int progress = job.totalFiles() == 0 ? 0
                : Math.min(100, job.processedFiles() * 100 / job.totalFiles());
        List<SecureCodingResultRow> results = List.copyOf(mapper.findResults(job.id()));
        String message = messageOverride == null ? statusMessage(job, results) : messageOverride;
        return new SecureCodingScanJobResponse(
                job.id(), job.projectKey(), job.status(), message, progress,
                job.totalFiles(), job.processedFiles(), job.passedFiles(), job.findingCount(),
                job.errorFiles(), job.createdAt(), job.startedAt(), job.completedAt(),
                results);
    }

    private String statusMessage(SecureCodingScanJob job, List<SecureCodingResultRow> results) {
        return switch (job.status()) {
            case "QUEUED" -> "점검 요청이 대기열에서 실행을 기다리고 있습니다.";
            case "RUNNING" -> "파일을 점검하고 있습니다. 화면을 벗어나도 서버에서 계속 처리됩니다.";
            case "COMPLETED" -> "모든 파일의 점검이 완료되었습니다.";
            case "COMPLETED_WITH_ERRORS" -> results.stream()
                    .filter(row -> "ERROR".equals(row.status()))
                    .map(SecureCodingResultRow::message)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("점검이 완료되었지만 일부 파일에서 오류가 발생했습니다.");
            case "FAILED" -> job.errorMessage() == null ? "점검 작업이 실패했습니다." : job.errorMessage();
            default -> "점검 상태를 확인하고 있습니다.";
        };
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Secure coding scan failed." : current.getMessage();
    }
}
