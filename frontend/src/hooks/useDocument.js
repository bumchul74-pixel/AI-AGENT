import { useCallback, useEffect, useRef, useState } from 'react';
import { deleteDocument, fetchDocuments, reindexDocument, uploadDocument, uploadProjectArchive } from '../api/documentApi.js';
import { isApiRequestError, notifyApp } from '../api/apiClient.js';

const DOCUMENT_PAGE_SIZE = 30;

function appendUniqueDocuments(currentDocuments, nextDocuments) {
  const nextById = new Map(currentDocuments.map((document) => [document.id, document]));
  nextDocuments.forEach((document) => {
    nextById.set(document.id, document);
  });
  return Array.from(nextById.values());
}

function notifyUnexpectedError(exception) {
  if (!isApiRequestError(exception)) {
    notifyApp(exception.message || '\uC694\uCCAD\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.', 'error');
  }
}

export function useDocument(projectKey, indexStatus = '') {
  const [documents, setDocuments] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [workingDocumentId, setWorkingDocumentId] = useState(null);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const loadingMoreRef = useRef(false);
  const uploadInFlightRef = useRef(false);

  const loadDocuments = useCallback(async () => {
    if (!projectKey) {
      setDocuments([]); setTotalCount(0); setHasNext(false); setPage(0);
      return [];
    }
    setIsLoading(true);

    try {
      const result = await fetchDocuments({ page: 0, size: DOCUMENT_PAGE_SIZE, projectKey, indexStatus });
      const nextDocuments = result.documents ?? [];
      setDocuments(nextDocuments);
      setPage(result.page ?? 0);
      setTotalCount(result.totalCount ?? nextDocuments.length);
      setHasNext(Boolean(result.hasNext));
      return nextDocuments;
    } catch (exception) {
      notifyUnexpectedError(exception);
      setDocuments([]);
      setPage(0);
      setTotalCount(0);
      setHasNext(false);
      return [];
    } finally {
      setIsLoading(false);
    }
  }, [indexStatus, projectKey]);

  const loadMoreDocuments = useCallback(async () => {
    if (isLoading || isLoadingMore || loadingMoreRef.current || !hasNext) {
      return [];
    }

    const nextPage = page + 1;
    loadingMoreRef.current = true;
    setIsLoadingMore(true);

    try {
      const result = await fetchDocuments({ page: nextPage, size: DOCUMENT_PAGE_SIZE, projectKey, indexStatus });
      const nextDocuments = result.documents ?? [];
      setDocuments((currentDocuments) => appendUniqueDocuments(currentDocuments, nextDocuments));
      setPage(result.page ?? nextPage);
      setTotalCount(result.totalCount ?? totalCount);
      setHasNext(Boolean(result.hasNext));
      return nextDocuments;
    } catch (exception) {
      notifyUnexpectedError(exception);
      return [];
    } finally {
      loadingMoreRef.current = false;
      setIsLoadingMore(false);
    }
  }, [hasNext, indexStatus, isLoading, isLoadingMore, page, projectKey, totalCount]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  async function upload({ file, documentType }) {
    if (!projectKey) {
      notifyApp('먼저 프로젝트를 선택해 주세요.', 'warning');
      return null;
    }
    if (!file) {
      notifyApp('\uC5C5\uB85C\uB4DC\uD560 \uD30C\uC77C\uC744 \uC120\uD0DD\uD574 \uC8FC\uC138\uC694.', 'warning');
      return null;
    }

    if (uploadInFlightRef.current) {
      return null;
    }
    uploadInFlightRef.current = true;

    setIsUploading(true);

    try {
      const isArchive = file.name?.toLowerCase().endsWith('.zip');
      const uploadedDocument = isArchive
        ? await uploadProjectArchive(file, projectKey)
        : await uploadDocument({ file, documentType, projectKey });
      await loadDocuments();
      return uploadedDocument;
    } catch (exception) {
      notifyUnexpectedError(exception);
      return null;
    } finally {
      uploadInFlightRef.current = false;
      setIsUploading(false);
    }
  }

  async function reindex(id) {
    setWorkingDocumentId(id);

    try {
      const indexedDocument = await reindexDocument(id);
      await loadDocuments();
      return indexedDocument;
    } catch (exception) {
      notifyUnexpectedError(exception);
      return null;
    } finally {
      setWorkingDocumentId(null);
    }
  }

  async function remove(id) {
    setWorkingDocumentId(id);

    try {
      await deleteDocument(id);
      await loadDocuments();
      return true;
    } catch (exception) {
      notifyUnexpectedError(exception);
      return false;
    } finally {
      setWorkingDocumentId(null);
    }
  }

  return {
    documents,
    hasNext,
    isLoading,
    isLoadingMore,
    isUploading,
    totalCount,
    workingDocumentId,
    loadDocuments,
    loadMoreDocuments,
    upload,
    reindex,
    remove,
  };
}
