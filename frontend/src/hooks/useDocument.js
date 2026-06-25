import { useCallback, useEffect, useState } from 'react';
import { deleteDocument, fetchDocuments, reindexDocument, uploadDocument } from '../api/documentApi.js';

export function useDocument() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [workingDocumentId, setWorkingDocumentId] = useState(null);

  const loadDocuments = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const nextDocuments = await fetchDocuments();
      setDocuments(nextDocuments);
      return nextDocuments;
    } catch (exception) {
      setError(exception.message);
      setDocuments([]);
      return [];
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  async function upload({ file, documentType }) {
    if (!file) {
      setError('업로드할 파일을 선택해 주세요.');
      return null;
    }

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
    isLoading,
    isUploading,
    workingDocumentId,
    loadDocuments,
    upload,
    reindex,
    remove,
  };
}
