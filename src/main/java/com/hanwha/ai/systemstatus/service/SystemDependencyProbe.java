package com.hanwha.ai.systemstatus.service;

import com.hanwha.ai.systemstatus.domain.SystemDependencyDescriptor;
import com.hanwha.ai.systemstatus.domain.SystemProbeOutcome;

public interface SystemDependencyProbe {
    SystemDependencyDescriptor descriptor();

    SystemProbeOutcome check();
}
