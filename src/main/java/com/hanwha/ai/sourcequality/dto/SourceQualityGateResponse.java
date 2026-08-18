package com.hanwha.ai.sourcequality.dto;

import java.util.List;

public record SourceQualityGateResponse(String status, List<String> reasons) {
}
