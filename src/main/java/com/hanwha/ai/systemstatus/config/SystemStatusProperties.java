package com.hanwha.ai.systemstatus.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "system-status")
public record SystemStatusProperties(
        Duration refreshInterval,
        Duration initialDelay,
        Duration connectTimeout
) {
    public SystemStatusProperties {
        refreshInterval = defaultDuration(refreshInterval, Duration.ofSeconds(30));
        initialDelay = defaultDuration(initialDelay, Duration.ofSeconds(2));
        connectTimeout = defaultDuration(connectTimeout, Duration.ofSeconds(2));
    }

    private static Duration defaultDuration(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
