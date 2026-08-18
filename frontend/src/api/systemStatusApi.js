import { apiRequest } from './apiClient.js';

export function fetchSystemStatus(signal = undefined) {
  return apiRequest('/api/system-status', {
    signal,
    errorMessage: '시스템 상태를 불러오지 못했습니다.',
  });
}

export function checkSystemStatus() {
  return apiRequest('/api/system-status/check', {
    method: 'POST',
    errorMessage: '시스템 상태 점검을 완료하지 못했습니다.',
  });
}
