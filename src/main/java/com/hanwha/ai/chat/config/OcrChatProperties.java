package com.hanwha.ai.chat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "ocr-chat")
public record OcrChatProperties(
        DataSize maxFileSize,
        int maxPdfPages,
        List<String> allowedExtensions
) {
    public OcrChatProperties {
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(20) : maxFileSize;
        maxPdfPages = maxPdfPages <= 0 ? 10 : maxPdfPages;
        allowedExtensions = allowedExtensions == null || allowedExtensions.isEmpty()
                ? List.of("pdf", "png", "jpg", "jpeg", "bmp", "gif", "tif", "tiff", "webp")
                : List.copyOf(allowedExtensions);
    }
}
