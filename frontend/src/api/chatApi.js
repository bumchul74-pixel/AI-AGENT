import { API_BASE_URL } from '../constants/apiConstants.js';

export async function sendChatMessage(message, file = null, conversationId = null) {
  const body = file ? new FormData() : JSON.stringify({ message, conversationId });
  if (file) {
    body.append('message', message);
    body.append('file', file);
    if (conversationId != null) {
      body.append('conversationId', String(conversationId));
    }
  }
  const response = await fetch(`${API_BASE_URL}/api/chat`, {
    method: 'POST',
    headers: file ? undefined : { 'Content-Type': 'application/json' },
    body,
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? '채팅 요청에 실패했습니다.');
  }

  return response.json();
}

export async function fetchChatAttachment(messageId, fileName) {
  const response = await fetch(
    `${API_BASE_URL}/api/chat/messages/${messageId}/attachment`,
  );
  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? '첨부파일을 불러오지 못했습니다.');
  }

  const contentType = response.headers.get('Content-Type') ?? 'text/x-java-source';
  return new File([await response.blob()], fileName, { type: contentType });
}

export async function fetchChatConversations() {
  const response = await fetch(`${API_BASE_URL}/api/chat/conversations`);
  if (!response.ok) {
    throw new Error('대화 목록을 불러오지 못했습니다.');
  }
  return response.json();
}

export async function fetchChatProjects() {
  const response = await fetch(`${API_BASE_URL}/api/chat/projects`);
  if (!response.ok) {
    throw new Error('프로젝트 목록을 불러오지 못했습니다.');
  }
  return response.json();
}

export async function createChatProject(name) {
  return sendProjectRequest(`${API_BASE_URL}/api/chat/projects`, 'POST', name);
}

export async function renameChatProject(projectId, name) {
  return sendProjectRequest(
    `${API_BASE_URL}/api/chat/projects/${projectId}`,
    'PATCH',
    name,
  );
}

export async function moveChatConversation(conversationId, projectId) {
  const response = await fetch(
    `${API_BASE_URL}/api/chat/conversations/${conversationId}/project`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId }),
    },
  );
  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? '대화를 이동하지 못했습니다.');
  }
}

export async function fetchConversationMessages(conversationId) {
  const response = await fetch(
    `${API_BASE_URL}/api/chat/conversations/${conversationId}/messages`,
  );
  if (!response.ok) {
    throw new Error('대화 내용을 불러오지 못했습니다.');
  }
  return response.json();
}

export async function deleteChatConversation(conversationId) {
  const response = await fetch(
    `${API_BASE_URL}/api/chat/conversations/${conversationId}`,
    { method: 'DELETE' },
  );
  if (!response.ok) {
    throw new Error('대화를 삭제하지 못했습니다.');
  }
}

async function sendProjectRequest(url, method, name) {
  const response = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? '프로젝트를 저장하지 못했습니다.');
  }
  return response.json();
}
