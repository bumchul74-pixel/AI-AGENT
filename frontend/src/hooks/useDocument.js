import { useState } from 'react';

export function useDocument() {
  const [documents, setDocuments] = useState([]);

  return { documents, setDocuments };
}
