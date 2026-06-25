package com.hanwha.ai.document.domain;

import com.hanwha.ai.global.exception.BusinessException;
import java.util.Locale;

public enum DocumentType {
    STANDARD_DOCUMENT,
    STANDARD_SOURCE;

    public static DocumentType resolve(String value, String fileName) {
        if (value != null && !value.isBlank()) {
            try {
                return DocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("Unsupported document type: " + value, exception);
            }
        }

        if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return STANDARD_SOURCE;
        }
        return STANDARD_DOCUMENT;
    }
}
