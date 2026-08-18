package com.hanwha.ai.systemstatus.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.systemstatus.domain.SystemDependencyDescriptor;
import com.hanwha.ai.systemstatus.domain.SystemHealthStatus;
import com.hanwha.ai.systemstatus.domain.SystemProbeOutcome;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SystemStatusServiceTest {
    @Test
    void returnsCachedSnapshotWithoutCallingProbes() {
        AtomicInteger calls = new AtomicInteger();
        SystemStatusService service = new SystemStatusService(List.of(probe(
                "backend", true, () -> {
                    calls.incrementAndGet();
                    return SystemProbeOutcome.up("ok");
                }
        )));

        var snapshot = service.getSnapshot();

        assertThat(calls).hasValue(0);
        assertThat(snapshot.status()).isEqualTo(SystemHealthStatus.UNKNOWN);
        assertThat(snapshot.systems()).singleElement()
                .extracting("status")
                .isEqualTo(SystemHealthStatus.UNKNOWN);
    }

    @Test
    void marksOverallDownWhenCriticalDependencyIsDown() {
        SystemStatusService service = new SystemStatusService(List.of(
                probe("backend", true, () -> SystemProbeOutcome.up("ok")),
                probe("postgresql", true, () -> SystemProbeOutcome.down("down")),
                probe("easyocr", false, () -> SystemProbeOutcome.down("down"))
        ));

        var snapshot = service.checkNow();

        assertThat(snapshot.status()).isEqualTo(SystemHealthStatus.DOWN);
        assertThat(snapshot.upCount()).isEqualTo(1);
        assertThat(snapshot.downCount()).isEqualTo(2);
    }

    @Test
    void marksOverallDegradedWhenOnlyOptionalDependencyIsDown() {
        SystemStatusService service = new SystemStatusService(List.of(
                probe("backend", true, () -> SystemProbeOutcome.up("ok")),
                probe("easyocr", false, () -> SystemProbeOutcome.down("down"))
        ));

        var snapshot = service.checkNow();

        assertThat(snapshot.status()).isEqualTo(SystemHealthStatus.DEGRADED);
        assertThat(snapshot.downCount()).isEqualTo(1);
    }

    @Test
    void convertsProbeExceptionToSafeDownStatus() {
        SystemStatusService service = new SystemStatusService(List.of(probe(
                "rag", true, () -> {
                    throw new IllegalStateException("secret endpoint detail");
                }
        )));

        var snapshot = service.checkNow();

        assertThat(snapshot.status()).isEqualTo(SystemHealthStatus.DOWN);
        assertThat(snapshot.systems()).singleElement()
                .extracting("message")
                .isEqualTo("상태 점검에 실패했습니다.");
    }

    private SystemDependencyProbe probe(
            String id,
            boolean critical,
            java.util.function.Supplier<SystemProbeOutcome> checker
    ) {
        return new DefaultSystemDependencyProbe(
                new SystemDependencyDescriptor(id, id, "TEST", critical, "TEST"),
                checker
        );
    }
}
