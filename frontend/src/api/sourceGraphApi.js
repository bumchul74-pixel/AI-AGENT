import { apiUrl } from '../constants/apiConstants.js';

async function readError(response, fallbackMessage) {
  const errorBody = await response.json().catch(() => null);
  return new Error(errorBody?.message ?? fallbackMessage);
}

function queryString({ query, limit } = {}) {
  const params = new URLSearchParams();
  if (query && query.trim()) {
    params.append('query', query.trim());
  }
  if (limit) {
    params.append('limit', String(limit));
  }

  const queryText = params.toString();
  return queryText ? `?${queryText}` : '';
}

export async function fetchSourceGraphOverview(filters = {}) {
  const response = await fetch(apiUrl(`/api/source-graph${queryString(filters)}`));

  if (!response.ok) {
    throw await readError(response, 'Source graph request failed.');
  }

  const graph = await response.json();
  if (!Array.isArray(graph?.nodes) || !Array.isArray(graph?.relationships)) {
    throw new Error('Source graph response is invalid.');
  }

  return graph;
}