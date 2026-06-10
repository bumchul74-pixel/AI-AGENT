import { API_BASE_URL } from '../constants/apiConstants.js';

export async function fetchDocuments() {
  const response = await fetch(`${API_BASE_URL}/api/documents`);

  if (!response.ok) {
    throw new Error('문서 목록을 불러오지 못했습니다.');
  }

  return response.json();
}
