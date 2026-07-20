import { useCallback, useEffect, useState } from 'react';
import {
  deleteChatConversation,
  fetchChatAttachment,
  fetchChatConversations,
  fetchConversationMessages,
  sendChatMessage,
} from '../api/chatApi.js';

export function useChat() {
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(true);
  const [resendingMessageId, setResendingMessageId] = useState(null);

  const refreshConversations = useCallback(async () => {
    const items = await fetchChatConversations();
    setConversations(items);
    return items;
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadInitialConversation() {
      setIsHistoryLoading(true);
      try {
        const items = await fetchChatConversations();
        if (cancelled) return;
        setConversations(items);
        if (items.length > 0) {
          const firstId = items[0].id;
          const history = await fetchConversationMessages(firstId);
          if (cancelled) return;
          setActiveConversationId(firstId);
          setMessages(history);
        }
      } catch (exception) {
        if (!cancelled) {
          setMessages([createErrorMessage(exception.message)]);
        }
      } finally {
        if (!cancelled) setIsHistoryLoading(false);
      }
    }

    loadInitialConversation();
    return () => { cancelled = true; };
  }, []);

  function appendMessage(message) {
    setMessages((current) => [...current, createMessage(message)]);
  }

  async function selectConversation(conversationId) {
    if (isLoading || conversationId === activeConversationId) return;
    setActiveConversationId(conversationId);
    setMessages([]);
    setIsHistoryLoading(true);
    try {
      setMessages(await fetchConversationMessages(conversationId));
    } catch (exception) {
      setMessages([createErrorMessage(exception.message)]);
    } finally {
      setIsHistoryLoading(false);
    }
  }

  function startNewConversation() {
    if (isLoading) return;
    setActiveConversationId(null);
    setMessages([]);
  }

  async function removeConversation(conversationId) {
    if (isLoading) return;
    await deleteChatConversation(conversationId);
    const items = await refreshConversations();
    if (conversationId !== activeConversationId) return;

    const next = items[0];
    if (!next) {
      startNewConversation();
      return;
    }
    setActiveConversationId(next.id);
    setMessages(await fetchConversationMessages(next.id));
  }

  async function submit(content, file = null) {
    const trimmed = content.trim();
    if (!trimmed || isLoading) return;

    appendMessage({
      role: 'user',
      content: trimmed,
      attachmentName: file?.name,
      attachmentFile: file,
    });
    setIsLoading(true);
    try {
      const response = await sendChatMessage(trimmed, file, activeConversationId);
      appendMessage({
        role: 'assistant',
        content: response.message ?? response.content ?? '응답 메시지가 비어 있습니다.',
      });
      if (response.conversationId != null) {
        setActiveConversationId(response.conversationId);
      }
      await refreshConversations();
    } catch (exception) {
      appendMessage({
        role: 'assistant',
        content: exception.message || '채팅 요청에 실패했습니다.',
        status: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  }

  async function resend(message) {
    if (message.role !== 'user' || isLoading || resendingMessageId != null) return;
    setResendingMessageId(message.id);
    try {
      let file = message.attachmentFile ?? null;
      if (message.attachmentName && !file) {
        if (typeof message.id !== 'number') {
          throw new Error('첨부파일 원본을 찾을 수 없습니다.');
        }
        file = await fetchChatAttachment(message.id, message.attachmentName);
      }
      await submit(message.content, file);
    } catch (exception) {
      appendMessage({
        role: 'assistant',
        content: exception.message || '메시지를 재전송하지 못했습니다.',
        status: 'error',
      });
    } finally {
      setResendingMessageId(null);
    }
  }

  return {
    conversations,
    activeConversationId,
    messages,
    isLoading,
    isHistoryLoading,
    resendingMessageId,
    appendMessage,
    selectConversation,
    startNewConversation,
    removeConversation,
    submit,
    resend,
  };
}

function createMessage(message) {
  return {
    id: crypto.randomUUID(),
    createdAt: new Date().toISOString(),
    ...message,
  };
}

function createErrorMessage(content) {
  return createMessage({ role: 'assistant', content, status: 'error' });
}
