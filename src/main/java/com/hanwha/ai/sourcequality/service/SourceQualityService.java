package com.hanwha.ai.sourcequality.service;

import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.sourcequality.domain.SourceQualityMethod;
import com.hanwha.ai.sourcequality.domain.SourceQualitySnapshot;
import com.hanwha.ai.sourcequality.domain.SourceQualityThreshold;
import com.hanwha.ai.sourcequality.dto.SourceQualityDashboardResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityDuplicateGroupDetailResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityDuplicateGroupResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityGateResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityMethodDetailResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityMethodResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualitySummaryResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityThresholdRequest;
import com.hanwha.ai.sourcequality.dto.SourceQualityThresholdResponse;
import com.hanwha.ai.sourcequality.dto.SourceQualityTrendResponse;
import com.hanwha.ai.sourcequality.mapper.SourceQualityMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SourceQualityService {
    private static final int TREND_LIMIT = 30;
    private final SourceQualityGraphReader graphReader;
    private final SourceQualityMapper mapper;
    private final RagDocumentRepository documentRepository;

    public SourceQualityService(SourceQualityGraphReader graphReader, SourceQualityMapper mapper,
                                RagDocumentRepository documentRepository) {
        this.graphReader = graphReader;
        this.mapper = mapper;
        this.documentRepository = documentRepository;
    }

    public SourceQualityDashboardResponse get(String projectKey) {
        return build(validateProject(projectKey), false);
    }

    public SourceQualityMethodDetailResponse getMethodDetail(String projectKey, String methodUid) {
        String normalizedProjectKey = validateProject(projectKey);
        String normalizedMethodUid = methodUid == null ? "" : methodUid.trim();
        if (!StringUtils.hasText(normalizedMethodUid) || normalizedMethodUid.length() > 512) {
            throw new BusinessException("Invalid source quality method.");
        }
        return graphReader.findMethodDetail(normalizedProjectKey, normalizedMethodUid)
                .map(SourceQualityMethodDetailResponse::from)
                .orElseThrow(() -> new BusinessException("Source quality method not found."));
    }

    public SourceQualityDuplicateGroupDetailResponse getDuplicateGroup(String projectKey,
                                                                        String type,
                                                                        String hash) {
        String normalizedProjectKey = validateProject(projectKey);
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String normalizedHash = hash == null ? "" : hash.trim();
        if (!("EXACT".equals(normalizedType) || "STRUCTURAL".equals(normalizedType))
                || !StringUtils.hasText(normalizedHash) || normalizedHash.length() > 128) {
            throw new BusinessException("Invalid duplicate method group.");
        }
        List<SourceQualityMethodDetailResponse> methods = graphReader
                .findDuplicateMethods(normalizedProjectKey, normalizedType, normalizedHash).stream()
                .map(SourceQualityMethodDetailResponse::from)
                .toList();
        if (methods.size() < 2) {
            throw new BusinessException("Duplicate method group not found.");
        }
        return new SourceQualityDuplicateGroupDetailResponse(
                normalizedType, normalizedHash, methods.size(), methods);
    }

    @Transactional
    public SourceQualityDashboardResponse evaluate(String projectKey) {
        return build(validateProject(projectKey), true);
    }

    @Transactional
    public SourceQualityDashboardResponse updateThresholds(String projectKey,
                                                            SourceQualityThresholdRequest request) {
        String normalizedProjectKey = validateProject(projectKey);
        if (request == null || request.cyclomaticComplexity() == null
                || request.cognitiveComplexity() == null || request.duplicateRatio() == null
                || request.minimumDuplicateLines() == null) {
            throw new BusinessException("All source quality thresholds are required.");
        }
        if (request.cyclomaticComplexity() < 1 || request.cognitiveComplexity() < 1
                || request.duplicateRatio() < 0 || request.duplicateRatio() > 100
                || request.minimumDuplicateLines() < 1) {
            throw new BusinessException("Source quality thresholds are out of range.");
        }
        SourceQualityThreshold threshold = SourceQualityThreshold.defaults(normalizedProjectKey);
        threshold.setCyclomaticComplexity(request.cyclomaticComplexity());
        threshold.setCognitiveComplexity(request.cognitiveComplexity());
        threshold.setDuplicateRatio(request.duplicateRatio());
        threshold.setMinimumDuplicateLines(request.minimumDuplicateLines());
        mapper.upsertThreshold(threshold);
        return build(normalizedProjectKey, true);
    }

    private SourceQualityDashboardResponse build(String projectKey, boolean saveSnapshot) {
        SourceQualityThreshold threshold = threshold(projectKey);
        List<SourceQualityMethod> methods = graphReader.findMethods(projectKey);
        List<SourceQualityDuplicateGroupResponse> groups = duplicateGroups(
                methods, threshold.getMinimumDuplicateLines());
        Set<String> duplicateMethodIds = new LinkedHashSet<>();
        groups.forEach(group -> group.methods().forEach(method -> duplicateMethodIds.add(method.methodUid())));
        List<SourceQualityMethod> allComplexMethods = methods.stream()
                .filter(method -> method.cyclomaticComplexity() > threshold.getCyclomaticComplexity()
                        || method.cognitiveComplexity() > threshold.getCognitiveComplexity())
                .sorted(Comparator.comparingInt(SourceQualityMethod::cyclomaticComplexity).reversed()
                        .thenComparing(Comparator.comparingInt(SourceQualityMethod::cognitiveComplexity).reversed())
                        .thenComparing(SourceQualityMethod::methodUid))
                .toList();
        List<SourceQualityMethodResponse> complexMethods = allComplexMethods.stream()
                .limit(100).map(SourceQualityMethodResponse::from).toList();
        double duplicateRatio = methods.isEmpty() ? 0.0
                : round(duplicateMethodIds.size() * 100.0 / methods.size());
        SourceQualitySummaryResponse summary = new SourceQualitySummaryResponse(
                methods.size(), duplicateMethodIds.size(), groups.size(), duplicateRatio,
                allComplexMethods.size(), methods.stream().mapToInt(SourceQualityMethod::cyclomaticComplexity).max().orElse(0),
                methods.stream().mapToInt(SourceQualityMethod::cognitiveComplexity).max().orElse(0));
        SourceQualityGateResponse gate = gate(summary, threshold);
        if (saveSnapshot) saveSnapshotIfChanged(projectKey, summary, gate.status());
        List<SourceQualityTrendResponse> trend = mapper.findSnapshots(projectKey, TREND_LIMIT).stream()
                .map(SourceQualityTrendResponse::from).toList();
        List<SourceQualityDuplicateGroupResponse> visibleGroups = groups.stream().limit(100).toList();
        return new SourceQualityDashboardResponse(projectKey, LocalDateTime.now(), summary,
                SourceQualityThresholdResponse.from(threshold), gate, visibleGroups, complexMethods, trend);
    }

    private List<SourceQualityDuplicateGroupResponse> duplicateGroups(List<SourceQualityMethod> methods,
                                                                       int minimumLines) {
        List<SourceQualityMethod> eligible = methods.stream()
                .filter(method -> method.lineCount() >= minimumLines).toList();
        List<SourceQualityDuplicateGroupResponse> result = new ArrayList<>();
        addGroups(result, "EXACT", eligible, SourceQualityMethod::methodHash);
        addGroups(result, "STRUCTURAL", eligible, SourceQualityMethod::structuralHash);
        return result.stream()
                .sorted(Comparator.comparingInt(SourceQualityDuplicateGroupResponse::duplicatedLineCount).reversed()
                        .thenComparing(SourceQualityDuplicateGroupResponse::type)
                        .thenComparing(SourceQualityDuplicateGroupResponse::hash))
                .toList();
    }

    private void addGroups(List<SourceQualityDuplicateGroupResponse> target, String type,
                           List<SourceQualityMethod> methods,
                           Function<SourceQualityMethod, String> hashProvider) {
        Map<String, List<SourceQualityMethod>> groups = new LinkedHashMap<>();
        for (SourceQualityMethod method : methods) {
            String hash = hashProvider.apply(method);
            if (StringUtils.hasText(hash)) groups.computeIfAbsent(hash, ignored -> new ArrayList<>()).add(method);
        }
        groups.forEach((hash, group) -> {
            if (group.size() < 2) return;
            List<SourceQualityMethodResponse> responses = group.stream()
                    .sorted(Comparator.comparing(SourceQualityMethod::filePath)
                            .thenComparingInt(SourceQualityMethod::startLine))
                    .map(SourceQualityMethodResponse::from).toList();
            target.add(new SourceQualityDuplicateGroupResponse(type, hash, group.size(),
                    group.stream().mapToInt(SourceQualityMethod::lineCount).sum(), responses));
        });
    }

    private SourceQualityGateResponse gate(SourceQualitySummaryResponse summary,
                                            SourceQualityThreshold threshold) {
        List<String> reasons = new ArrayList<>();
        if (summary.highComplexityCount() > 0) {
            reasons.add("복잡도 임계치를 초과한 메서드가 " + summary.highComplexityCount() + "개입니다.");
        }
        if (summary.duplicateRatio() > threshold.getDuplicateRatio()) {
            reasons.add("중복 메서드 비율이 " + threshold.getDuplicateRatio() + "% 임계치를 초과했습니다.");
        }
        return new SourceQualityGateResponse(reasons.isEmpty() ? "PASS" : "FAIL", List.copyOf(reasons));
    }

    private SourceQualityThreshold threshold(String projectKey) {
        SourceQualityThreshold threshold = mapper.findThreshold(projectKey);
        return threshold == null ? SourceQualityThreshold.defaults(projectKey) : threshold;
    }

    private void saveSnapshotIfChanged(String projectKey, SourceQualitySummaryResponse summary,
                                       String gateStatus) {
        SourceQualitySnapshot latest = mapper.findLatestSnapshot(projectKey);
        if (sameSummary(latest, summary)) return;
        SourceQualitySnapshot snapshot = new SourceQualitySnapshot();
        snapshot.setProjectKey(projectKey);
        snapshot.setTotalMethodCount(summary.totalMethodCount());
        snapshot.setDuplicateMethodCount(summary.duplicateMethodCount());
        snapshot.setDuplicateGroupCount(summary.duplicateGroupCount());
        snapshot.setDuplicateRatio(summary.duplicateRatio());
        snapshot.setHighComplexityCount(summary.highComplexityCount());
        snapshot.setMaxCyclomaticComplexity(summary.maxCyclomaticComplexity());
        snapshot.setMaxCognitiveComplexity(summary.maxCognitiveComplexity());
        snapshot.setGateStatus(gateStatus);
        mapper.insertSnapshot(snapshot);
    }

    private boolean sameSummary(SourceQualitySnapshot latest, SourceQualitySummaryResponse summary) {
        return latest != null && latest.getTotalMethodCount() == summary.totalMethodCount()
                && latest.getDuplicateMethodCount() == summary.duplicateMethodCount()
                && latest.getDuplicateGroupCount() == summary.duplicateGroupCount()
                && Math.abs(latest.getDuplicateRatio() - summary.duplicateRatio()) < 0.0001
                && latest.getHighComplexityCount() == summary.highComplexityCount()
                && latest.getMaxCyclomaticComplexity() == summary.maxCyclomaticComplexity()
                && latest.getMaxCognitiveComplexity() == summary.maxCognitiveComplexity();
    }

    private String validateProject(String projectKey) {
        String normalized = projectKey == null ? "" : projectKey.trim();
        if (!StringUtils.hasText(normalized) || !documentRepository.projectExists(normalized)) {
            throw new BusinessException("Project not found.");
        }
        return normalized;
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
