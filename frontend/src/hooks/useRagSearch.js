import { useState } from 'react';
import { searchRag } from '../api/ragApi.js';
import { isApiRequestError } from '../api/apiClient.js';

export function useRagSearch() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  async function search({ query, topK, projectKey }) {
    if (!query.trim()) {
      setError('검색어를 입력해 주세요.');
      setDocuments([]);
      return null;
    }

    setIsLoading(true);
    setError('');

    try {
      const response = await searchRag({ query, topK, projectKey });
      const nextDocuments = response.documents ?? [];
      setDocuments(nextDocuments);
      return nextDocuments;
    } catch (exception) {
      setDocuments([]);
      setError(isApiRequestError(exception) ? '' : exception.message);
      return null;
    } finally {
      setIsLoading(false);
    }
  }

  return {
    documents,
    error,
    isLoading,
    search,
  };
}
