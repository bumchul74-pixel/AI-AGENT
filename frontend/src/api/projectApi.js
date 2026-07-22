import { apiRequest } from './apiClient.js';

export async function fetchKnowledgeProjects() {
  return apiRequest('/api/knowledge/projects', { errorMessage: '요청을 처리하지 못했습니다.' });
}

export async function createKnowledgeProject(project) {
  return apiRequest('/api/knowledge/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(project),
    errorMessage: '프로젝트를 생성하지 못했습니다.',
  });
}

export async function updateKnowledgeProject(projectKey, project) {
  return apiRequest(`/api/knowledge/projects/${encodeURIComponent(projectKey)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(project),
    errorMessage: '프로젝트를 수정하지 못했습니다.',
  });
}

export async function deleteKnowledgeProject(projectKey) {
  return apiRequest(`/api/knowledge/projects/${encodeURIComponent(projectKey)}`, {
    method: 'DELETE',
    responseType: 'none',
    errorMessage: '프로젝트를 삭제하지 못했습니다.',
  });
}
