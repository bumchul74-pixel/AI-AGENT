import { API_BASE_URL } from '../constants/apiConstants.js';

export async function generateCode({ targetType, prompt }) {
  const response = await fetch(`${API_BASE_URL}/api/generations`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ targetType, prompt }),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? '코드 생성 요청에 실패했습니다.');
  }

  return response.json();
}
