import { useState } from 'react';
import { sendChatMessage } from '../api/chatApi.js';

export function useChat() {
  const [messages, setMessages] = useState([
    {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '안녕하세요. RAG 문서와 프로젝트 소스 기준으로 Spring Boot 코드 생성을 도와드릴게요.',
      createdAt: new Date().toISOString(),
    },
  ]);
  const [isLoading, setIsLoading] = useState(false);

  function appendMessage(message) {
    setMessages((current) => [
      ...current,
      {
        id: crypto.randomUUID(),
        createdAt: new Date().toISOString(),
        ...message,
      },
    ]);
  }

  async function submit(content) {
    const trimmed = content.trim();

    if (!trimmed || isLoading) {
      return;
    }

    appendMessage({ role: 'user', content: trimmed });
    setIsLoading(true);

    try {
      const response = await sendChatMessage(trimmed);
      appendMessage({
        role: 'assistant',
        content: response.message ?? response.content ?? '응답 메시지가 비어 있습니다.',
      });
    } catch {
      appendMessage({
        role: 'assistant',
        content: `현재 채팅 API가 준비되지 않았습니다. 백엔드 /api/chat 구현 후 실제 LLM 응답이 연결됩니다.\n\n요청 내용: ${trimmed}`,
        status: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  }

  return { messages, isLoading, appendMessage, submit };
}
