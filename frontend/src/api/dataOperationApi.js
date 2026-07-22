import { deleteDocument, fetchDocuments } from './documentApi.js';
import { apiRequest } from './apiClient.js';

async function fetchIndexedSources({ page, size, projectKey }) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (projectKey) params.set('projectKey', projectKey);
  return apiRequest(`/api/rag/sources?${params.toString()}`, {
    errorMessage: 'VectorDB 색인 목록을 조회하지 못했습니다.',
  });
}

function fileName(source) {
  const path = source.fileName || source.filePath || source.sourceKey || '';
  return path.split(/[\\/]/).filter(Boolean).at(-1) || source.sourceKey;
}

export async function fetchCleanupTargets({ page = 0, size = 30, projectKey } = {}) {
  const [postgresPage, indexPage] = await Promise.all([
    fetchDocuments({ page, size, projectKey }),
    fetchIndexedSources({ page, size, projectKey }),
  ]);
  const targets = new Map();
  (indexPage.sources ?? []).forEach((source) => {
    targets.set(source.sourceKey, {
      ...source, id: source.sourceKey, originalFileName: fileName(source), documentType: 'SOURCE_INDEX',
      postgresTracked: false, vectorTracked: Boolean(source.vectorTracked), graphTracked: Boolean(source.graphTracked), createdAt: null,
    });
  });
  postgresPage.documents.forEach((document) => {
    const sourceKey = `document:${document.id}`;
    const indexedSource = targets.get(sourceKey);
    targets.set(sourceKey, {
      ...indexedSource, ...document, id: sourceKey, documentId: document.id, sourceKey,
      postgresTracked: true,
      vectorTracked: Boolean(indexedSource?.vectorTracked),
      graphTracked: Boolean(indexedSource?.graphTracked),
    });
  });
  return {
    targets: [...targets.values()].sort((left, right) => left.originalFileName.localeCompare(right.originalFileName)),
    page, size, totalCount: postgresPage.totalCount + (indexPage.totalCount ?? 0),
    hasNext: postgresPage.hasNext || Boolean(indexPage.hasNext),
  };
}

async function deleteIndexedSource(sourceKey, graphKey) {
  const params = new URLSearchParams({ sourceKey });
  if (graphKey) params.set('graphKey', graphKey);
  return apiRequest(`/api/rag/sources?${params.toString()}`, {
    method: 'DELETE',
    responseType: 'none',
    errorMessage: `${sourceKey} 삭제에 실패했습니다.`,
  });
}

export async function deleteCleanupTargets(targets, onProgress) {
  const deletedIds = [];
  const failures = [];
  for (const target of targets) {
    try {
      if (target.postgresTracked && target.documentId != null) await deleteDocument(target.documentId);
      else await deleteIndexedSource(target.sourceKey, target.graphKey);
      deletedIds.push(target.id);
    } catch (error) {
      failures.push({ documentId: target.id, sourceKey: target.sourceKey, message: error.message });
    }
    onProgress?.({ completed: deletedIds.length + failures.length, total: targets.length });
  }
  return { deletedIds, failures };
}
