import { useState } from 'react';
import { searchRag } from '../api/ragApi.js';
import { isApiRequestError, notifyApp } from '../api/apiClient.js';

export function useRagSearch() {
  const [documents, setDocuments] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  async function search({ query, topK, projectKey }) {
    if (!query.trim()) {
      notifyApp('검색어를 입력해 주세요.', 'warning');
      setDocuments([]);
      return null;
    }

    setIsLoading(true);

    try {
      const response = await searchRag({ query, topK, projectKey });
      const nextDocuments = response.documents ?? [];
      setDocuments(nextDocuments);
      return nextDocuments;
    } catch (exception) {
      setDocuments([]);
      if (!isApiRequestError(exception)) {
        notifyApp(exception.message || 'RAG 검색 요청을 처리하지 못했습니다.', 'error');
      }
      return null;
    } finally {
      setIsLoading(false);
    }
  }

  return {
    documents,
    isLoading,
    search,
  };
}
