import { useMemo, useState } from 'react';

export function useGenerationStore() {
  const [history, setHistory] = useState([]);

  return useMemo(() => ({
    history,
    addHistory(item) {
      setHistory((current) => [item, ...current].slice(0, 10));
    },
  }), [history]);
}
