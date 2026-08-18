package com.hanwha.ai.systemstatus.domain;

public record SystemDependencyDescriptor(
        String id,
        String name,
        String category,
        boolean critical,
        String checkType
) {
}
