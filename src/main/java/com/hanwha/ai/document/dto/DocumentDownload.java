package com.hanwha.ai.document.dto;

import org.springframework.core.io.Resource;

public record DocumentDownload(
        String fileName,
        String contentType,
        Resource resource
) {
}
