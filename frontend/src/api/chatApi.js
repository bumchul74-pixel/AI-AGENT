import { apiRequest } from './apiClient.js';

export async function sendChatMessage(message, file = null, conversationId = null) {
  const body = file ? new FormData() : JSON.stringify({ message, conversationId });
  if (file) {
    body.append('message', message);
    body.append('file', file);
    if (conversationId != null) {
      body.append('conversationId', String(conversationId));
    }
  }
  return apiRequest('/api/chat', {
    method: 'POST',
    headers: file ? undefined : { 'Content-Type': 'application/json' },
    body,
    errorMessage: '채팅 요청에 실패했습니다.',
  });
}

export async function fetchChatAttachment(messageId, fileName) {
  const { data, response } = await apiRequest(`/api/chat/messages/${messageId}/attachment`, {
    responseType: 'blob',
    includeResponse: true,
    errorMessage: '첨부파일을 불러오지 못했습니다.',
  });
  const contentType = response.headers.get('Content-Type') ?? 'text/x-java-source';
  return new File([data], fileName, { type: contentType });
}

export async function fetchChatConversations() {
  return apiRequest('/api/chat/conversations', { errorMessage: '대화 목록을 불러오지 못했습니다.' });
}

export async function fetchChatProjects() {
  return apiRequest('/api/chat/projects', { errorMessage: '요청을 처리하지 못했습니다.' });
}

export async function createChatProject(name) {
  return sendProjectRequest('/api/chat/projects', 'POST', name);
}

export async function renameChatProject(projectId, name) {
  return sendProjectRequest(
    `/api/chat/projects/${projectId}`,
    'PATCH',
    name,
  );
}

export async function moveChatConversation(conversationId, projectId) {
  return apiRequest(`/api/chat/conversations/${conversationId}/project`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId }),
      responseType: 'none',
      errorMessage: '대화를 이동하지 못했습니다.',
  });
}

export async function fetchConversationMessages(conversationId) {
  return apiRequest(`/api/chat/conversations/${conversationId}/messages`, {
    errorMessage: '대화 내용을 불러오지 못했습니다.',
  });
}

export async function deleteChatConversation(conversationId) {
  return apiRequest(`/api/chat/conversations/${conversationId}`, {
    method: 'DELETE',
    responseType: 'none',
    errorMessage: '대화를 삭제하지 못했습니다.',
  });
}

async function sendProjectRequest(url, method, name) {
  return apiRequest(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
    errorMessage: '프로젝트를 저장하지 못했습니다.',
  });
}
