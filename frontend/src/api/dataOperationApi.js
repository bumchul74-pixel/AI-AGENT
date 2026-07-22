import { apiUrl } from '../constants/apiConstants.js';
import { deleteDocument, fetchDocuments } from './documentApi.js';

async function readError(response, fallbackMessage) {
  const body = await response.json().catch(() => null);
  return body?.message ?? body?.detail ?? fallbackMessage;
}

async function fetchIndexedSources({ page, size }) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const response = await fetch(apiUrl(`/api/rag/sources?${params.toString()}`));
  if (!response.ok) {
    throw new Error(await readError(response, 'VectorDB 인벤토리를 조회하지 못했습니다.'));
  }
  return response.json();
}

function fileName(source) {
  const path = source.fileName || source.filePath || source.sourceKey || '';
  return path.split(/[\\/]/).filter(Boolean).at(-1) || source.sourceKey;
}

export async function fetchCleanupTargets({ page = 0, size = 30 } = {}) {
  const [postgresPage, indexPage] = await Promise.all([
    fetchDocuments({ page, size }),
    fetchIndexedSources({ page, size }),
  ]);
  const targets = new Map();

  (indexPage.sources ?? []).forEach((source) => {
    targets.set(source.sourceKey, {
      ...source,
      id: source.sourceKey,
      originalFileName: fileName(source),
      documentType: 'SOURCE_INDEX',
      postgresTracked: false,
      vectorTracked: Boolean(source.vectorTracked),
      graphTracked: Boolean(source.graphTracked),
      createdAt: null,
    });
  });

  postgresPage.documents.forEach((document) => {
    const sourceKey = `document:${document.id}`;
    targets.set(sourceKey, {
      ...targets.get(sourceKey),
      ...document,
      id: sourceKey,
      documentId: document.id,
      sourceKey,
      postgresTracked: true,
      vectorTracked: targets.has(sourceKey),
    });
  });

  return {
    targets: [...targets.values()].sort((left, right) =>
      left.originalFileName.localeCompare(right.originalFileName)),
    page,
    size,
    totalCount: postgresPage.totalCount + (indexPage.totalCount ?? 0),
    hasNext: postgresPage.hasNext || Boolean(indexPage.hasNext),
  };
}

async function deleteIndexedSource(sourceKey, graphKey) {
  const params = new URLSearchParams({ sourceKey });
  if (graphKey) params.set('graphKey', graphKey);
  const response = await fetch(apiUrl(`/api/rag/sources?${params.toString()}`), { method: 'DELETE' });
  if (!response.ok) {
    throw new Error(await readError(response, `${sourceKey} 삭제에 실패했습니다.`));
  }
}

export async function deleteCleanupTargets(targets, onProgress) {
  const deletedIds = [];
  const failures = [];

  for (const target of targets) {
    try {
      if (target.postgresTracked && target.documentId != null) {
        await deleteDocument(target.documentId);
      } else {
        await deleteIndexedSource(target.sourceKey, target.graphKey);
      }
      deletedIds.push(target.id);
    } catch (error) {
      failures.push({ documentId: target.id, sourceKey: target.sourceKey, message: error.message });
    }
    onProgress?.({ completed: deletedIds.length + failures.length, total: targets.length });
  }

  return { deletedIds, failures };
}
