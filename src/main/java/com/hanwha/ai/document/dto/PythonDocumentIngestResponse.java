package com.hanwha.ai.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonDocumentIngestResponse(
        @JsonProperty("stored_count") int storedCount
) {
}
