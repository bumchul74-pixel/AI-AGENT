import { useCallback, useEffect, useRef, useState } from 'react';
import {
  createChatProject,
  deleteChatConversation,
  deleteChatProject,
  fetchChatAttachment,
  fetchChatConversations,
  fetchChatProjects,
  fetchConversationMessages,
  moveChatConversation,
  renameChatProject,
  sendChatMessage,
} from '../api/chatApi.js';
import {
  isApiRequestCancelledError,
  isApiRequestError,
  notifyApp,
} from '../api/apiClient.js';

export function useChat() {
  const [conversations, setConversations] = useState([]);
  const [projects, setProjects] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(true);
  const [resendingMessageId, setResendingMessageId] = useState(null);
  const [projectError, setProjectError] = useState('');
  const [pendingRequests, setPendingRequests] = useState([]);
  const [isProjectLoading, setIsProjectLoading] = useState(false);
  const pendingRequestsRef = useRef([]);
  const activeConversationIdRef = useRef(null);
  const requestControllerRef = useRef(null);

  const refreshConversations = useCallback(async (signal = undefined) => {
    const items = await fetchChatConversations(signal);
    setConversations(items);
    return items;
  }, []);

  const refreshProjects = useCallback(async () => {
    const items = await fetchChatProjects();
    setProjects(items);
    return items;
  }, []);

  useEffect(() => () => {
    pendingRequestsRef.current = [];
    const requestController = requestControllerRef.current;
    requestControllerRef.current = null;
    requestController?.abort();
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadInitialData() {
      setIsHistoryLoading(true);
      try {
        const [items, projectItems] = await Promise.all([
          fetchChatConversations(),
          fetchChatProjects(),
        ]);
        if (cancelled) return;
        setConversations(items);
        setProjects(projectItems);
        setActiveConversationId(null);
        setMessages([]);
      } catch (exception) {
        if (!cancelled && !isApiRequestError(exception)) {
          setMessages([createErrorMessage(exception.message)]);
        }
      } finally {
        if (!cancelled) setIsHistoryLoading(false);
      }
    }

    loadInitialData();
    return () => { cancelled = true; };
  }, []);

  function appendMessage(message) {
    setMessages((current) => [...current, createMessage(message)]);
  }

  function updatePendingRequests(next) {
    pendingRequestsRef.current = next;
    setPendingRequests(next);
  }

  async function selectConversation(conversationId) {

    if (requestControllerRef.current || pendingRequestsRef.current.length > 0 || conversationId === activeConversationId) return;
    activeConversationIdRef.current = conversationId;
    setActiveConversationId(conversationId);
    setMessages([]);
    setIsHistoryLoading(true);
    try {
      setMessages(await fetchConversationMessages(conversationId));
    } catch (exception) {
      if (!isApiRequestError(exception)) setMessages([createErrorMessage(exception.message)]);
    } finally {
      setIsHistoryLoading(false);
    }
  }

  function startNewConversation() {
    if (requestControllerRef.current || pendingRequestsRef.current.length > 0) return;
    activeConversationIdRef.current = null;
    setActiveConversationId(null);
    setMessages([]);
  }

  async function removeConversation(conversationId) {
    if (requestControllerRef.current || pendingRequestsRef.current.length > 0) return;
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
    activeConversationIdRef.current = next.id;
  }

  async function createProject(name) {
    setProjectError('');
    setIsProjectLoading(true);
    try {
      const project = await createChatProject(name);
      await refreshProjects();
      return project;
    } catch (exception) {
      setProjectError(isApiRequestError(exception) ? '' : exception.message);
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
      setProjectError(isApiRequestError(exception) ? '' : exception.message);
      throw exception;
    } finally {
      setIsProjectLoading(false);
    }
  }

  async function deleteProject(projectId) {
    setProjectError('');
    setIsProjectLoading(true);
    try {
      await deleteChatProject(projectId);
      await Promise.all([refreshConversations(), refreshProjects()]);
    } catch (exception) {
      setProjectError(isApiRequestError(exception) ? '' : exception.message);
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
      setProjectError(isApiRequestError(exception) ? '' : exception.message);
      throw exception;
    } finally {
      setIsProjectLoading(false);
    }
  }

  function submit(content, file = null) {
    const trimmed = content.trim()
      || (file ? '첨부파일에서 텍스트를 추출해 주세요.' : '');
    if (!trimmed) return Promise.resolve({ queued: false });

    const request = {
      id: crypto.randomUUID(),
      content: trimmed,
      file,
      attachmentName: file?.name ?? null,
    };
    if (requestControllerRef.current) {
      updatePendingRequests([...pendingRequestsRef.current, request]);
      return Promise.resolve({ queued: true });
    }
    return executeRequest(request);
  }

  async function executeRequest(request) {
    appendMessage({
      role: 'user',
      content: request.content,
      attachmentName: request.attachmentName,
      attachmentFile: request.file,
    });
    const requestController = new AbortController();
    requestControllerRef.current = requestController;
    setIsLoading(true);
    try {
      const response = await sendChatMessage(
        request.content,
        request.file,
        activeConversationIdRef.current,
        requestController.signal,
      );
      appendMessage({
        role: 'assistant',
        content: response.message ?? response.content ?? '응답 메시지가 비어 있습니다.',
        mcpContextApplied: Boolean(response.mcpContextApplied),
        mcpReference: response.mcpReference || null,
        agentExecution: response.agentExecution || null,
      });
      if (response.conversationId != null) {
        setActiveConversationId(response.conversationId);
        activeConversationIdRef.current = response.conversationId;
      }
      await refreshConversations(requestController.signal);
    } catch (exception) {
      if (isApiRequestCancelledError(exception)) return;
      if (!isApiRequestError(exception)) {
        appendMessage({
          role: 'assistant',
          content: exception.message || '채팅 요청에 실패했습니다.',
          status: 'error',
        });
      }
    } finally {
      if (requestControllerRef.current === requestController) {
        requestControllerRef.current = null;
        const [nextRequest, ...remainingRequests] = pendingRequestsRef.current;
        updatePendingRequests(remainingRequests);
        if (nextRequest) {
          void executeRequest(nextRequest);
        } else {
          setIsLoading(false);
        }
      }
    }
  }


  function updatePendingRequest(requestId, content) {
    const trimmed = content.trim();
    if (!trimmed) return false;
    updatePendingRequests(pendingRequestsRef.current.map((request) => (
      request.id === requestId ? { ...request, content: trimmed } : request
    )));
    return true;
  }

  function removePendingRequest(requestId) {
    updatePendingRequests(pendingRequestsRef.current.filter((request) => request.id !== requestId));
  }
  function stopResponse() {
    const requestController = requestControllerRef.current;
    if (!requestController) return;
    requestController.abort();
    notifyApp('응답 요청을 중지했습니다.', 'warning');
  }

  async function resend(message) {
    if (message.role !== 'user' || requestControllerRef.current || pendingRequestsRef.current.length > 0 || resendingMessageId != null) return;
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
      if (!isApiRequestError(exception)) {
        appendMessage({
          role: 'assistant',
          content: exception.message || '메시지를 재전송하지 못했습니다.',
          status: 'error',
        });
      }
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
    pendingRequests,
    appendMessage,
    selectConversation,
    startNewConversation,
    removeConversation,
    createProject,
    renameProject,
    deleteProject,
    moveConversation,
    submit,
    removePendingRequest,
    updatePendingRequest,
    resend,
    stopResponse,
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
