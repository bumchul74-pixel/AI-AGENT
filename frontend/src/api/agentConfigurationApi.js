import { apiRequest } from './apiClient.js';

export function fetchActiveAgentConfiguration(signal = undefined) {
  return apiRequest('/api/admin/agent-configurations/active', {
    signal,
    errorMessage: 'Agent 설정을 불러오지 못했습니다.',
  });
}

export function saveAndActivateAgentConfiguration(configuration) {
  return apiRequest('/api/admin/agent-configurations/active', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ configuration }),
    errorMessage: 'Agent 설정을 저장하고 활성화하지 못했습니다.',
  });
}

export function refreshAgentConfiguration() {
  return apiRequest('/api/admin/agent-configurations/refresh', {
    method: 'POST',
    errorMessage: 'DB의 활성 Agent 설정을 반영하지 못했습니다.',
  });
}
