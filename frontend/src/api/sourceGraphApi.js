import { apiRequest, apiResponseError } from './apiClient.js';

function queryString({ query, limit, projectKey } = {}) {
  const params = new URLSearchParams();
  if (query && query.trim()) {
    params.append('query', query.trim());
  }
  if (limit) {
    params.append('limit', String(limit));
  }
  if (projectKey) params.append('projectKey', projectKey);

  const queryText = params.toString();
  return queryText ? `?${queryText}` : '';
}

export async function fetchSourceGraphOverview(filters = {}) {
  const graph = await apiRequest(`/api/source-graph${queryString(filters)}`, {
    errorMessage: 'Source graph request failed.',
  });
  if (!Array.isArray(graph?.nodes) || !Array.isArray(graph?.relationships)) {
    throw apiResponseError('Source graph response is invalid.');
  }

  return graph;
}
export async function fetchSourceGraphNodeSource(nodeId) {
  const params = new URLSearchParams({ nodeId });
  const source = await apiRequest(`/api/source-graph/node-source?${params.toString()}`, {
    errorMessage: 'Source graph node source request failed.',
  });
  if (!source || typeof source.available !== 'boolean') {
    throw apiResponseError('Source graph node source response is invalid.');
  }

  return source;
}
