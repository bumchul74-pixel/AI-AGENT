import { apiUrl } from '../constants/apiConstants.js';

async function readError(response, fallbackMessage) {
  const errorBody = await response.json().catch(() => null);
  return errorBody?.message ?? errorBody?.detail ?? fallbackMessage;
}

export async function fetchDocuments({ page = 0, size = 30 } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  const response = await fetch(apiUrl(`/api/documents/page?${params.toString()}`));

  if (!response.ok) {
    throw new Error(await readError(response, '臾몄꽌 紐⑸줉??遺덈윭?ㅼ? 紐삵뻽?듬땲??'));
  }

  const payload = await response.json();
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

export async function uploadDocument({ file, documentType }) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('documentType', documentType);

  const response = await fetch(apiUrl('/api/documents'), {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(await readError(response, '臾몄꽌 ?낅줈?쒖뿉 ?ㅽ뙣?덉뒿?덈떎.'));
  }

  return response.json();
}

export async function reindexDocument(id) {
  const response = await fetch(apiUrl(`/api/documents/${id}/reindex`), {
    method: 'POST',
  });

  if (!response.ok) {
    throw new Error(await readError(response, '臾몄꽌 ?ъ깋?몄뿉 ?ㅽ뙣?덉뒿?덈떎.'));
  }

  return response.json();
}

export async function deleteDocument(id) {
  const response = await fetch(apiUrl(`/api/documents/${id}`), {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(await readError(response, '臾몄꽌 ??젣???ㅽ뙣?덉뒿?덈떎.'));
  }
}

export function documentDownloadUrl(id) {
  return apiUrl(`/api/documents/${id}/download`);
}
