package com.hanwha.ai.systemstatus.service;

import com.hanwha.ai.systemstatus.domain.SystemDependencyDescriptor;
import com.hanwha.ai.systemstatus.domain.SystemHealthStatus;
import com.hanwha.ai.systemstatus.domain.SystemProbeOutcome;
import com.hanwha.ai.systemstatus.dto.SystemDependencyStatusResponse;
import com.hanwha.ai.systemstatus.dto.SystemStatusResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {
    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);

    private final List<SystemDependencyProbe> probes;
    private final AtomicReference<SystemStatusResponse> snapshot;

    public SystemStatusService(List<SystemDependencyProbe> probes) {
        this.probes = probes.stream()
                .sorted(Comparator.comparing(probe -> probe.descriptor().id()))
                .toList();
        this.snapshot = new AtomicReference<>(unknownSnapshot(this.probes));
    }

    public SystemStatusResponse getSnapshot() {
        return snapshot.get();
    }

    public synchronized SystemStatusResponse checkNow() {
        List<SystemDependencyStatusResponse> systems = probes.stream()
                .map(this::safeCheck)
                .toList();
        SystemStatusResponse refreshed = aggregate(systems, Instant.now());
        snapshot.set(refreshed);
        return refreshed;
    }

    @Scheduled(
            initialDelayString = "${system-status.initial-delay:2s}",
            fixedDelayString = "${system-status.refresh-interval:30s}"
    )
    public void refreshInBackground() {
        checkNow();
    }

    private SystemDependencyStatusResponse safeCheck(SystemDependencyProbe probe) {
        SystemDependencyDescriptor descriptor = probe.descriptor();
        long startedAt = System.nanoTime();
        Instant checkedAt = Instant.now();
        try {
            SystemProbeOutcome outcome = probe.check();
            return response(descriptor, outcome, elapsedMillis(startedAt), checkedAt);
        } catch (RuntimeException exception) {
            log.warn(
                    "System status probe failed. system={} errorType={}",
                    descriptor.id(),
                    exception.getClass().getSimpleName()
            );
            return response(
                    descriptor,
                    SystemProbeOutcome.down("상태 점검에 실패했습니다."),
                    elapsedMillis(startedAt),
                    checkedAt
            );
        }
    }

    private SystemDependencyStatusResponse response(
            SystemDependencyDescriptor descriptor,
            SystemProbeOutcome outcome,
            long latencyMs,
            Instant checkedAt
    ) {
        return new SystemDependencyStatusResponse(
                descriptor.id(), descriptor.name(), descriptor.category(), outcome.status(),
                descriptor.critical(), descriptor.checkType(), latencyMs, checkedAt, outcome.message()
        );
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static SystemStatusResponse unknownSnapshot(List<SystemDependencyProbe> probes) {
        Instant now = Instant.now();
        List<SystemDependencyStatusResponse> systems = probes.stream()
                .map(SystemDependencyProbe::descriptor)
                .map(descriptor -> new SystemDependencyStatusResponse(
                        descriptor.id(), descriptor.name(), descriptor.category(),
                        SystemHealthStatus.UNKNOWN, descriptor.critical(), descriptor.checkType(),
                        0, now, "아직 점검하지 않았습니다."
                ))
                .toList();
        return aggregate(systems, now);
    }

    private static SystemStatusResponse aggregate(
            List<SystemDependencyStatusResponse> systems,
            Instant checkedAt
    ) {
        int up = count(systems, SystemHealthStatus.UP);
        int degraded = count(systems, SystemHealthStatus.DEGRADED);
        int down = count(systems, SystemHealthStatus.DOWN);
        int unknown = count(systems, SystemHealthStatus.UNKNOWN);
        boolean criticalDown = systems.stream()
                .anyMatch(system -> system.critical() && system.status() == SystemHealthStatus.DOWN);
        SystemHealthStatus overall = criticalDown
                ? SystemHealthStatus.DOWN
                : down > 0 || degraded > 0 || (unknown > 0 && up > 0)
                ? SystemHealthStatus.DEGRADED
                : unknown == systems.size() ? SystemHealthStatus.UNKNOWN : SystemHealthStatus.UP;
        return new SystemStatusResponse(
                overall, checkedAt, systems.size(), up, degraded, down, unknown, systems
        );
    }

    private static int count(List<SystemDependencyStatusResponse> systems, SystemHealthStatus status) {
        return (int) systems.stream().filter(system -> system.status() == status).count();
    }
}
