import { API_BASE_URL } from '../constants/apiConstants.js';

export async function searchRag({ query, topK = 5 }) {
  const response = await fetch(`${API_BASE_URL}/api/rag/search`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, top_k: topK }),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? 'RAG 검색 요청에 실패했습니다.');
  }

  return response.json();
}

export async function fetchRagStats() {
  const response = await fetch(`${API_BASE_URL}/api/rag/stats`);

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? 'RAG 통계 조회에 실패했습니다.');
  }

  return response.json();
}
