import { useState } from 'react';
import { generateCode } from '../api/generateApi.js';

export function useGenerate() {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  async function submit(payload) {
    setIsLoading(true);
    setError('');

    try {
      const response = await generateCode(payload);
      setResult(response);
      return response;
    } catch (exception) {
      setError(exception.message);
      return null;
    } finally {
      setIsLoading(false);
    }
  }

  function clearError() {
    setError('');
  }

  return {
    result,
    error,
    isLoading,
    submit,
    clearError,
  };
}
