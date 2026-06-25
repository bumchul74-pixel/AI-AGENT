import { apiUrl } from '../constants/apiConstants.js';

async function readError(response, fallbackMessage) {
  const errorBody = await response.json().catch(() => null);
  return errorBody?.message ?? errorBody?.detail ?? fallbackMessage;
}

export async function fetchDocuments() {
  const response = await fetch(apiUrl('/api/documents'));

  if (!response.ok) {
    throw new Error(await readError(response, '문서 목록을 불러오지 못했습니다.'));
  }

  return response.json();
}

export async function uploadDocument({ file, documentType }) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('documentType', documentType);

  const response = await fetch(apiUrl('/api/documents'), {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(await readError(response, '문서 업로드에 실패했습니다.'));
  }

  return response.json();
}

export async function reindexDocument(id) {
  const response = await fetch(apiUrl(`/api/documents/${id}/reindex`), {
    method: 'POST',
  });

  if (!response.ok) {
    throw new Error(await readError(response, '문서 재색인에 실패했습니다.'));
  }

  return response.json();
}

export async function deleteDocument(id) {
  const response = await fetch(apiUrl(`/api/documents/${id}`), {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(await readError(response, '문서 삭제에 실패했습니다.'));
  }
}

export function documentDownloadUrl(id) {
  return apiUrl(`/api/documents/${id}/download`);
}
