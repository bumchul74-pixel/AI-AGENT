import { apiRequest } from './apiClient.js';

function projectPath(projectKey) {
  return `/api/source-quality/projects/${encodeURIComponent(projectKey)}`;
}

export function fetchSourceQuality(projectKey, signal) {
  return apiRequest(projectPath(projectKey), {
    signal,
    errorMessage: '소스 품질 정보를 조회하지 못했습니다.',
  });
}

export function fetchSourceQualityMethodDetail(projectKey, methodUid, signal) {
  const query = new URLSearchParams({ methodUid });
  return apiRequest(`${projectPath(projectKey)}/methods/detail?${query}`, {
    signal,
    errorMessage: '고복잡도 메서드 내용을 조회하지 못했습니다.',
  });
}

export function fetchSourceQualityDuplicateGroup(projectKey, type, hash, signal) {
  return apiRequest(
    `${projectPath(projectKey)}/duplicate-groups/${encodeURIComponent(type)}/${encodeURIComponent(hash)}`,
    {
      signal,
      errorMessage: '중복 메서드 내용을 조회하지 못했습니다.',
    },
  );
}

export function evaluateSourceQuality(projectKey) {
  return apiRequest(`${projectPath(projectKey)}/evaluate`, {
    method: 'POST',
    errorMessage: '소스 품질 평가를 완료하지 못했습니다.',
  });
}

export function updateSourceQualityThresholds(projectKey, thresholds) {
  return apiRequest(`${projectPath(projectKey)}/thresholds`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(thresholds),
    errorMessage: '품질 임계치를 저장하지 못했습니다.',
  });
}
