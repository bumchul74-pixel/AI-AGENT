package com.hanwha.ai.securecoding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secure-coding.worker")
public record SecureCodingWorkerProperties(
        int coreSize, int maxSize, int queueCapacity, int coordinatorQueueCapacity
) {
    public SecureCodingWorkerProperties {
        coreSize = coreSize <= 0 ? 2 : coreSize;
        maxSize = maxSize < coreSize ? coreSize : maxSize;
        queueCapacity = queueCapacity <= 0 ? 100 : queueCapacity;
        coordinatorQueueCapacity = coordinatorQueueCapacity <= 0 ? 20 : coordinatorQueueCapacity;
    }
}
