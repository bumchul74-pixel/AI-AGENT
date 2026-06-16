import { useState } from 'react';
import { searchRag } from '../api/ragApi.js';

export function useRagSearch() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  async function search({ query, topK }) {
    if (!query.trim()) {
      setError('검색어를 입력해 주세요.');
      setDocuments([]);
      return null;
    }

    setIsLoading(true);
    setError('');

    try {
      const response = await searchRag({ query, topK });
      const nextDocuments = response.documents ?? [];
      setDocuments(nextDocuments);
      return nextDocuments;
    } catch (exception) {
      setDocuments([]);
      setError(exception.message);
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
