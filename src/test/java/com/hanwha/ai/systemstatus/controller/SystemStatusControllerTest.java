package com.hanwha.ai.systemstatus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.systemstatus.domain.SystemDependencyDescriptor;
import com.hanwha.ai.systemstatus.domain.SystemProbeOutcome;
import com.hanwha.ai.systemstatus.service.DefaultSystemDependencyProbe;
import com.hanwha.ai.systemstatus.service.SystemStatusService;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SystemStatusControllerTest {
    @Test
    void separatesCachedReadFromExplicitCheck() {
        AtomicInteger calls = new AtomicInteger();
        var probe = new DefaultSystemDependencyProbe(
                new SystemDependencyDescriptor("backend", "Backend", "CORE", true, "LIVENESS"),
                () -> {
                    calls.incrementAndGet();
                    return SystemProbeOutcome.up("ok");
                }
        );
        SystemStatusController controller = new SystemStatusController(
                new SystemStatusService(List.of(probe))
        );

        var cached = controller.status();
        var checked = controller.check();

        assertThat(calls).hasValue(1);
        assertThat(cached.unknownCount()).isEqualTo(1);
        assertThat(checked.upCount()).isEqualTo(1);
    }
}
