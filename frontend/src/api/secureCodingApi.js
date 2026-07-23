import { apiRequest } from './apiClient.js';

export function scanProjectSecureCoding(projectKey) {
  return apiRequest(`/api/secure-coding/projects/${encodeURIComponent(projectKey)}/scan`, {
    method: 'POST',
    errorMessage: '프로젝트 Secure Coding 점검에 실패했습니다.',
  });
}

export function getSecureCodingScan(jobId) {
  return apiRequest(`/api/secure-coding/scans/${encodeURIComponent(jobId)}`, {
    errorMessage: 'Secure Coding 점검 진행 상태를 조회하지 못했습니다.',
  });
}

export function getLatestSecureCodingScan(projectKey) {
  return apiRequest(`/api/secure-coding/projects/${encodeURIComponent(projectKey)}/scans/latest`, {
    errorMessage: '최근 Secure Coding 점검 결과를 조회하지 못했습니다.',
  });
}

export function getSecureCodingSource(documentId) {
  return apiRequest(`/api/documents/${encodeURIComponent(documentId)}/download`, {
    responseType: 'text',
    errorMessage: '소스 파일 내용을 불러오지 못했습니다.',
  });
}

export function exportSecureCodingResults(projectKey, rows) {
  return apiRequest('/api/secure-coding/export', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectKey, rows }),
    responseType: 'blob',
    errorMessage: 'Secure Coding 결과 Excel 생성에 실패했습니다.',
  });
}
