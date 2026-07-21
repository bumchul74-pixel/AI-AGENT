import { useCallback, useEffect, useState } from 'react';
import {
  createChatProject,
  deleteChatConversation,
  fetchChatAttachment,
  fetchChatConversations,
  fetchChatProjects,
  fetchConversationMessages,
  moveChatConversation,
  renameChatProject,
  sendChatMessage,
} from '../api/chatApi.js';

export function useChat() {
  const [conversations, setConversations] = useState([]);
  const [projects, setProjects] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(true);
  const [resendingMessageId, setResendingMessageId] = useState(null);
  const [projectError, setProjectError] = useState('');
  const [isProjectLoading, setIsProjectLoading] = useState(false);

  const refreshConversations = useCallback(async () => {
    const items = await fetchChatConversations();
    setConversations(items);
    return items;
  }, []);

  const refreshProjects = useCallback(async () => {
    const items = await fetchChatProjects();
    setProjects(items);
    return items;
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadInitialConversation() {
      setIsHistoryLoading(true);
      try {
        const [items, projectItems] = await Promise.all([
          fetchChatConversations(),
          fetchChatProjects(),
        ]);
        if (cancelled) return;
        setConversations(items);
        setProjects(projectItems);
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
    await refreshProjects();
    if (conversationId !== activeConversationId) return;

    const next = items[0];
    if (!next) {
      startNewConversation();
      return;
    }
    setActiveConversationId(next.id);
    setMessages(await fetchConversationMessages(next.id));
  }

  async function createProject(name) {
    setProjectError('');
    setIsProjectLoading(true);
    try {
      const project = await createChatProject(name);
      await refreshProjects();
      return project;
    } catch (exception) {
      setProjectError(exception.message);
      throw exception;
    } finally {
      setIsProjectLoading(false);
    }
  }

  async function renameProject(projectId, name) {
    setProjectError('');
    setIsProjectLoading(true);
    try {
      await renameChatProject(projectId, name);
      await refreshProjects();
    } catch (exception) {
      setProjectError(exception.message);
      throw exception;
    } finally {
      setIsProjectLoading(false);
    }
  }

  async function moveConversation(conversationId, projectId) {
    setProjectError('');
    setIsProjectLoading(true);
    try {
      await moveChatConversation(conversationId, projectId);
      await Promise.all([refreshConversations(), refreshProjects()]);
    } catch (exception) {
      setProjectError(exception.message);
      throw exception;
    } finally {
      setIsProjectLoading(false);
    }
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
    projects,
    activeConversationId,
    messages,
    isLoading,
    isHistoryLoading,
    resendingMessageId,
    projectError,
    isProjectLoading,
    appendMessage,
    selectConversation,
    startNewConversation,
    removeConversation,
    createProject,
    renameProject,
    moveConversation,
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
