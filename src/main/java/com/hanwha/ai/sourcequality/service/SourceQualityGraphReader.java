package com.hanwha.ai.sourcequality.service;

import com.hanwha.ai.sourcequality.domain.SourceQualityMethod;
import com.hanwha.ai.sourcequality.domain.SourceQualityMethodDetail;
import java.util.List;
import java.util.Optional;

public interface SourceQualityGraphReader {
    List<SourceQualityMethod> findMethods(String projectKey);

    List<SourceQualityMethodDetail> findDuplicateMethods(String projectKey, String type, String hash);

    Optional<SourceQualityMethodDetail> findMethodDetail(String projectKey, String methodUid);
}
