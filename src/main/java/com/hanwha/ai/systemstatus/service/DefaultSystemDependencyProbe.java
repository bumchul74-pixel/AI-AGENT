package com.hanwha.ai.systemstatus.service;

import com.hanwha.ai.systemstatus.domain.SystemDependencyDescriptor;
import com.hanwha.ai.systemstatus.domain.SystemProbeOutcome;
import java.util.function.Supplier;

public class DefaultSystemDependencyProbe implements SystemDependencyProbe {
    private final SystemDependencyDescriptor descriptor;
    private final Supplier<SystemProbeOutcome> checker;

    public DefaultSystemDependencyProbe(
            SystemDependencyDescriptor descriptor,
            Supplier<SystemProbeOutcome> checker
    ) {
        this.descriptor = descriptor;
        this.checker = checker;
    }

    @Override
    public SystemDependencyDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public SystemProbeOutcome check() {
        return checker.get();
    }
}
