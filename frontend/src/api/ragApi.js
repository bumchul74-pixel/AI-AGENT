import { apiRequest } from './apiClient.js';

export async function searchRag({ query, topK = 5, projectKey }) {
  return apiRequest('/api/rag/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, top_k: topK, projectId: projectKey }),
    errorMessage: 'RAG 검색 요청에 실패했습니다.',
  });
}

export async function fetchRagStats() {
  return apiRequest('/api/rag/stats', { errorMessage: 'RAG 통계 조회에 실패했습니다.' });
}
