import { apiRequest, apiResponseError } from './apiClient.js';

function queryString(filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.append(key, String(value).trim());
    }
  });

  const query = params.toString();
  return query ? `?${query}` : '';
}

export async function fetchProjectStructures() {
  const projectStructures = await apiRequest('/api/generations/project-structures', {
    errorMessage: 'Project structure list request failed.',
  });
  if (!Array.isArray(projectStructures)) {
    throw apiResponseError('Project structure list response is invalid.');
  }

  return projectStructures;
}

export async function generateCode({ targetTypes, prompt, projectKey }) {
  return apiRequest('/api/generations', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ targetTypes, prompt, projectKey }),
    errorMessage: 'Code generation request failed.',
  });
}

export async function fetchGenerationHistory(filters = {}) {
  const history = await apiRequest(`/api/generations/history${queryString(filters)}`, {
    errorMessage: '생성 이력을 조회하지 못했습니다.',
  });
  if (!Array.isArray(history)) {
    throw apiResponseError('생성 이력 응답 형식이 올바르지 않습니다.');
  }

  return history;
}

export async function fetchGenerationHistoryDetail(id) {
  return apiRequest(`/api/generations/history/${id}`, {
    errorMessage: '생성 이력 상세 정보를 조회하지 못했습니다.',
  });
}
