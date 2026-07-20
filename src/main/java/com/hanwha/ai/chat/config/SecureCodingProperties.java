package com.hanwha.ai.chat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "secure-coding")
public record SecureCodingProperties(
        DataSize maxFileSize,
        List<String> allowedExtensions,
        List<String> ruleSets
) {
    public SecureCodingProperties {
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(1) : maxFileSize;
        allowedExtensions = allowedExtensions == null || allowedExtensions.isEmpty()
                ? List.of("java")
                : List.copyOf(allowedExtensions);
        ruleSets = ruleSets == null ? List.of() : List.copyOf(ruleSets);
    }
}