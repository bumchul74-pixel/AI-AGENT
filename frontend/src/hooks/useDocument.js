import { useCallback, useEffect, useRef, useState } from 'react';
import { deleteDocument, fetchDocuments, reindexDocument, uploadDocument } from '../api/documentApi.js';

const DOCUMENT_PAGE_SIZE = 30;

function appendUniqueDocuments(currentDocuments, nextDocuments) {
  const nextById = new Map(currentDocuments.map((document) => [document.id, document]));
  nextDocuments.forEach((document) => {
    nextById.set(document.id, document);
  });
  return Array.from(nextById.values());
}

export function useDocument() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState('');
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
    setIsLoading(true);
    setError('');

    try {
      const result = await fetchDocuments({ page: 0, size: DOCUMENT_PAGE_SIZE });
      const nextDocuments = result.documents ?? [];
      setDocuments(nextDocuments);
      setPage(result.page ?? 0);
      setTotalCount(result.totalCount ?? nextDocuments.length);
      setHasNext(Boolean(result.hasNext));
      return nextDocuments;
    } catch (exception) {
      setError(exception.message);
      setDocuments([]);
      setPage(0);
      setTotalCount(0);
      setHasNext(false);
      return [];
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadMoreDocuments = useCallback(async () => {
    if (isLoading || isLoadingMore || loadingMoreRef.current || !hasNext) {
      return [];
    }

    const nextPage = page + 1;
    loadingMoreRef.current = true;
    setIsLoadingMore(true);
    setError('');

    try {
      const result = await fetchDocuments({ page: nextPage, size: DOCUMENT_PAGE_SIZE });
      const nextDocuments = result.documents ?? [];
      setDocuments((currentDocuments) => appendUniqueDocuments(currentDocuments, nextDocuments));
      setPage(result.page ?? nextPage);
      setTotalCount(result.totalCount ?? totalCount);
      setHasNext(Boolean(result.hasNext));
      return nextDocuments;
    } catch (exception) {
      setError(exception.message);
      return [];
    } finally {
      loadingMoreRef.current = false;
      setIsLoadingMore(false);
    }
  }, [hasNext, isLoading, isLoadingMore, page, totalCount]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  async function upload({ file, documentType }) {
    if (!file) {
      setError('\uC5C5\uB85C\uB4DC\uD560 \uD30C\uC77C\uC744 \uC120\uD0DD\uD574 \uC8FC\uC138\uC694.');
      return null;
    }

    if (uploadInFlightRef.current) {
      return null;
    }
    uploadInFlightRef.current = true;

    setIsUploading(true);
    setError('');

    try {
      const uploadedDocument = await uploadDocument({ file, documentType });
      await loadDocuments();
      return uploadedDocument;
    } catch (exception) {
      setError(exception.message);
      return null;
    } finally {
      uploadInFlightRef.current = false;
      setIsUploading(false);
    }
  }

  async function reindex(id) {
    setWorkingDocumentId(id);
    setError('');

    try {
      const indexedDocument = await reindexDocument(id);
      await loadDocuments();
      return indexedDocument;
    } catch (exception) {
      setError(exception.message);
      return null;
    } finally {
      setWorkingDocumentId(null);
    }
  }

  async function remove(id) {
    setWorkingDocumentId(id);
    setError('');

    try {
      await deleteDocument(id);
      await loadDocuments();
      return true;
    } catch (exception) {
      setError(exception.message);
      return false;
    } finally {
      setWorkingDocumentId(null);
    }
  }

  return {
    documents,
    error,
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
