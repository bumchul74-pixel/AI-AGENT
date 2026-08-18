import { apiRequest } from './apiClient.js';

export async function fetchDocuments({ page = 0, size = 30, projectKey, indexStatus } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (projectKey) params.set('projectKey', projectKey);
  if (indexStatus) params.set('indexStatus', indexStatus);
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

export async function reindexProjectJavaDocuments(projectKey, onProgress) {
  const documents = [];
  let page = 0;
  let hasNext = false;

  do {
    const result = await fetchDocuments({ page, size: 100, projectKey });
    documents.push(...result.documents);
    hasNext = result.hasNext;
    page += 1;
  } while (hasNext);

  const javaDocuments = documents.filter((document) => {
    const path = String(document.originalFileName ?? '').replaceAll('\\', '/').toLowerCase();
    return path.endsWith('.java') && !path.includes('/src/test/');
  });
  const failures = [];

  for (let index = 0; index < javaDocuments.length; index += 1) {
    const document = javaDocuments[index];
    onProgress?.({ completed: index, total: javaDocuments.length, currentFile: document.originalFileName });
    try {
      await reindexDocument(document.id);
    } catch (error) {
      failures.push({
        documentId: document.id,
        fileName: document.originalFileName,
        message: error instanceof Error ? error.message : String(error),
      });
    }
    onProgress?.({ completed: index + 1, total: javaDocuments.length, currentFile: document.originalFileName });
  }

  return {
    total: javaDocuments.length,
    successCount: javaDocuments.length - failures.length,
    failureCount: failures.length,
    failures,
  };
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
