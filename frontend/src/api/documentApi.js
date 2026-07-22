import { apiRequest } from './apiClient.js';

export async function fetchDocuments({ page = 0, size = 30, projectKey } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (projectKey) params.set('projectKey', projectKey);
  const payload = await apiRequest(`/api/documents/page?${params.toString()}`, {
    errorMessage: '문서 목록을 불러오지 못했습니다.',
  });
  if (Array.isArray(payload)) {
    return {
      documents: payload,
      page,
      size,
      totalCount: payload.length,
      hasNext: false,
    };
  }

  return {
    documents: payload?.documents ?? [],
    page: payload?.page ?? page,
    size: payload?.size ?? size,
    totalCount: payload?.totalCount ?? 0,
    hasNext: Boolean(payload?.hasNext),
  };
}

export async function uploadDocument({ file, documentType, projectKey }) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('documentType', documentType);
  formData.append('projectKey', projectKey);

  return apiRequest('/api/documents', {
    method: 'POST',
    body: formData,
    errorMessage: '문서 업로드에 실패했습니다.',
  });
}

export async function uploadProjectArchive(file, projectKey) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('projectKey', projectKey);
  return apiRequest('/api/documents/project-archive', {
    method: 'POST',
    body: formData,
    errorMessage: 'ZIP 프로젝트 업로드 및 색인에 실패했습니다.',
  });
}

export async function reindexDocument(id) {
  return apiRequest(`/api/documents/${id}/reindex`, {
    method: 'POST',
    errorMessage: '문서 재색인에 실패했습니다.',
  });
}

export async function deleteDocument(id) {
  return apiRequest(`/api/documents/${id}`, {
    method: 'DELETE',
    responseType: 'none',
    errorMessage: '문서 삭제에 실패했습니다.',
  });
}

export function downloadDocument(id) {
  return apiRequest(`/api/documents/${id}/download`, {
    responseType: 'blob',
    errorMessage: '문서를 다운로드하지 못했습니다.',
  });
}
