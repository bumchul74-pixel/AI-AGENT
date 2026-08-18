import { apiRequest } from './apiClient.js';

export function fetchNeo4jNodes({ label = '', keyword = '', page = 0, size = 30 }, signal) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (label.trim()) query.set('label', label.trim());
  if (keyword.trim()) query.set('keyword', keyword.trim());
  return apiRequest(`/api/neo4j-explorer/nodes?${query}`, {
    signal,
    errorMessage: 'Neo4j 노드 목록을 불러오지 못했습니다.',
  });
}

export function fetchNeo4jNodeDetail(elementId, signal) {
  return apiRequest(`/api/neo4j-explorer/nodes/${encodeURIComponent(elementId)}`, {
    signal,
    errorMessage: 'Neo4j 노드 상세 정보를 불러오지 못했습니다.',
  });
}