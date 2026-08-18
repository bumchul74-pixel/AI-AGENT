package com.hanwha.ai.systemstatus.domain;

public record SystemProbeOutcome(SystemHealthStatus status, String message) {
    public static SystemProbeOutcome up(String message) {
        return new SystemProbeOutcome(SystemHealthStatus.UP, message);
    }

    public static SystemProbeOutcome down(String message) {
        return new SystemProbeOutcome(SystemHealthStatus.DOWN, message);
    }

    public static SystemProbeOutcome unknown(String message) {
        return new SystemProbeOutcome(SystemHealthStatus.UNKNOWN, message);
    }
}
