import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowUp, Check, Layers3, MoreHorizontal, Paperclip, Pencil, Sparkles, Square, Trash2, X } from 'lucide-react';
import { ChatConversationList } from '../components/chat/ChatConversationList.jsx';
import { ChatInput } from '../components/chat/ChatInput.jsx';
import { ChatMessage } from '../components/chat/ChatMessage.jsx';
import { ChatNavigator } from '../components/chat/ChatNavigator.jsx';
import { useChat } from '../hooks/useChat.js';

export function ChatPage() {
  const chat = useChat();
  const [input, setInput] = useState('');
  const [attachment, setAttachment] = useState(null);
  const [responseElapsedSeconds, setResponseElapsedSeconds] = useState(0);
  const [activeRequestIndex, setActiveRequestIndex] = useState(0);
  const [stackMenuId, setStackMenuId] = useState(null);
  const [editingStackId, setEditingStackId] = useState(null);
  const [editingStackValue, setEditingStackValue] = useState('');
  const [stackMenuPosition, setStackMenuPosition] = useState({ left: 0, top: 0 });
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);
  const chatThreadRef = useRef(null);
  const userMessageElementsRef = useRef(new Map());
  const scrollFrameRef = useRef(null);
  const userMessages = useMemo(
    () => chat.messages.filter((message) => message.role === 'user'),
    [chat.messages],
  );
  const hasPendingRequests = chat.pendingRequests.length > 0;

  useEffect(() => {
    const thread = chatThreadRef.current;
    if (!thread) return;
    thread.scrollTo({
      top: thread.scrollHeight,
      behavior: chat.isLoading ? 'smooth' : 'auto',
    });
  }, [chat.messages, chat.isLoading, chat.isHistoryLoading]);

  useEffect(() => {
    const frameId = window.requestAnimationFrame(updateActiveRequest);
    return () => window.cancelAnimationFrame(frameId);
  }, [userMessages]);

  useEffect(() => () => {
    if (scrollFrameRef.current != null) {
      window.cancelAnimationFrame(scrollFrameRef.current);
    }
  }, []);

  useEffect(() => {
    if (stackMenuId == null) return undefined;
    const closeMenu = () => {
      setStackMenuId(null);
      setStackMenuPosition({ left: 0, top: 0 });
    };
    window.addEventListener('click', closeMenu);
    window.addEventListener('blur', closeMenu);
    return () => {
      window.removeEventListener('click', closeMenu);
      window.removeEventListener('blur', closeMenu);
    };
  }, [stackMenuId]);
  useEffect(() => {
    if (!chat.isLoading) {
      setResponseElapsedSeconds(0);
      return undefined;
    }

    const startedAt = performance.now();
    const updateElapsedTime = () => {
      setResponseElapsedSeconds(Math.floor((performance.now() - startedAt) / 1000));
    };
    updateElapsedTime();
    const timerId = window.setInterval(updateElapsedTime, 250);
    return () => window.clearInterval(timerId);
  }, [chat.isLoading]);

  function handleSubmit(event) {
    event.preventDefault();
    const nextInput = input;
    const nextAttachment = attachment;
    setInput('');
    setAttachment(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    if (textareaRef.current) {
      textareaRef.current.style.height = '';
    }
    chat.submit(nextInput, nextAttachment);
  }

  function handleAttachmentClear(event) {
    event.preventDefault();
    event.stopPropagation();
    setAttachment(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  function handleInputChange(value, textarea) {
    setInput(value);
    if (!textarea) return;
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`;
  }

  function setUserMessageElement(messageId, element) {
    if (element) userMessageElementsRef.current.set(messageId, element);
    else userMessageElementsRef.current.delete(messageId);
  }

  function updateActiveRequest() {
    const thread = chatThreadRef.current;
    if (!thread || userMessages.length === 0) {
      setActiveRequestIndex(0);
      return;
    }

    const threadTop = thread.getBoundingClientRect().top;
    const focusLine = thread.scrollTop + Math.min(thread.clientHeight * 0.25, 120);
    let nextIndex = 0;
    userMessages.forEach((message, index) => {
      const element = userMessageElementsRef.current.get(message.id);
      if (!element) return;
      const messageTop = element.getBoundingClientRect().top - threadTop + thread.scrollTop;
      if (messageTop <= focusLine) nextIndex = index;
    });
    setActiveRequestIndex((current) => (current === nextIndex ? current : nextIndex));
  }

  function handleThreadScroll() {
    if (scrollFrameRef.current != null) return;
    scrollFrameRef.current = window.requestAnimationFrame(() => {
      scrollFrameRef.current = null;
      updateActiveRequest();
    });
  }

  function navigateToRequest(index) {
    const thread = chatThreadRef.current;
    const message = userMessages[index];
    const element = message && userMessageElementsRef.current.get(message.id);
    if (!thread || !element) return;

    const targetTop = element.getBoundingClientRect().top
      - thread.getBoundingClientRect().top
      + thread.scrollTop
      - 14;
    setActiveRequestIndex(index);
    thread.scrollTo({ top: Math.max(0, targetTop), behavior: 'smooth' });
  }

  function openStackMenu(event, requestId) {
    event.preventDefault();
    event.stopPropagation();
    if (stackMenuId === requestId) {
      setStackMenuId(null);
      return;
    }
    const bounds = event.currentTarget.getBoundingClientRect();
    setStackMenuPosition({
      left: Math.max(8, Math.min(window.innerWidth - 120, bounds.right - 112)),
      top: Math.max(8, bounds.top - 68),
    });
    setStackMenuId(requestId);
  }

  function startStackEdit(request) {
    setStackMenuId(null);
    setEditingStackId(request.id);
    setEditingStackValue(request.content);
  }

  function cancelStackEdit() {
    setEditingStackId(null);
    setEditingStackValue('');
  }

  function saveStackEdit(requestId) {
    if (!chat.updatePendingRequest(requestId, editingStackValue)) return;
    cancelStackEdit();
  }

  function removeStackRequest(requestId) {
    setStackMenuId(null);
    if (editingStackId === requestId) cancelStackEdit();
    chat.removePendingRequest(requestId);
  }

  return (
    <section className="chat-page">
      <div className="chat-layout">
        <ChatConversationList
          conversations={chat.conversations}
          projects={chat.projects}
          activeConversationId={chat.activeConversationId}
          disabled={chat.isLoading || hasPendingRequests || chat.isHistoryLoading || chat.isProjectLoading || chat.resendingMessageId != null}
          onNew={chat.startNewConversation}
          onSelect={chat.selectConversation}
          onDelete={chat.removeConversation}
          onCreateProject={chat.createProject}
          onRenameProject={chat.renameProject}
          onDeleteProject={chat.deleteProject}
          onMoveConversation={chat.moveConversation}
          projectError={chat.projectError}
        />

        <div className="card chat-thread-panel">
          <div className={userMessages.length >= 3 ? 'chat-thread-stage has-navigator' : 'chat-thread-stage'}>
            <div className="chat-thread" ref={chatThreadRef} onScroll={handleThreadScroll}>
              {chat.isHistoryLoading && (
                <div className="chat-thread-empty">대화를 불러오는 중...</div>
              )}
              {!chat.isHistoryLoading && chat.messages.length === 0 && (
                <div className="chat-thread-empty">
                  <Sparkles size={24} />
                  <strong>새 대화를 시작하세요.</strong>
                  <span>첫 메시지가 전송되면 대화 제목과 이력이 자동 저장됩니다.</span>
                </div>
              )}
              {chat.messages.map((message) => (
                <ChatMessage
                  key={message.id}
                  message={message}
                  messageRef={message.role === 'user'
                    ? (element) => setUserMessageElement(message.id, element)
                    : undefined}
                  onResend={chat.resend}
                  resendDisabled={chat.isLoading || chat.resendingMessageId != null}
                  isResending={chat.resendingMessageId === message.id}
                />
              ))}
              {chat.isLoading && (
                <article className="chat-message assistant chat-loading-message">
                  <div className="chat-avatar">
                    <Sparkles size={17} />
                  </div>
                  <div className="chat-loading-content">
                    <div className="chat-loading-progress" aria-hidden="true">
                      <span>요청 처리 중</span>
                      <time dateTime={`PT${responseElapsedSeconds}S`}>
                        {formatElapsedTime(responseElapsedSeconds)}
                      </time>
                    </div>
                    <div className="chat-bubble chat-loading-bubble" role="status" aria-label="AI 응답 생성 중">
                      <div className="typing-dots">
                        <span />
                        <span />
                        <span />
                      </div>
                    </div>
                  </div>
                </article>
              )}
            </div>
            <ChatNavigator
              activeIndex={activeRequestIndex}
              messages={userMessages}
              onNavigate={navigateToRequest}
            />
          </div>

          <form className="chat-composer" onSubmit={handleSubmit}>
            {hasPendingRequests && (
              <section className="chat-request-stack" aria-label={'\uB300\uAE30 \uC911\uC778 \uC694\uCCAD \uC2A4\uD0DD'}>
                <header><Layers3 size={14} /><strong>{'\uC694\uCCAD \uC2A4\uD0DD'}</strong><span>{chat.pendingRequests.length}</span></header>
                <ol>
                  {chat.pendingRequests.map((request, index) => (
                    <li key={request.id} className={editingStackId === request.id ? 'is-editing' : ''}
                      onContextMenu={(event) => openStackMenu(event, request.id)}>
                      <span className="chat-request-stack-order">{index === 0 ? '\uB2E4\uC74C' : index + 1}</span>
                      {editingStackId === request.id ? (
                        <input className="chat-request-stack-input" value={editingStackValue} autoFocus
                          aria-label={'\uB300\uAE30 \uC694\uCCAD \uBA54\uC2DC\uC9C0 \uC218\uC815'}
                          onChange={(event) => setEditingStackValue(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter') {
                              event.preventDefault();
                              saveStackEdit(request.id);
                            }
                            if (event.key === 'Escape') cancelStackEdit();
                          }} />
                      ) : <strong title={request.content}>{request.content}</strong>}
                      {editingStackId !== request.id && request.attachmentName && (
                        <small title={request.attachmentName}><Paperclip size={11} /> {request.attachmentName}</small>
                      )}
                      {editingStackId === request.id ? (
                        <span className="chat-request-stack-edit-actions">
                          <button type="button" aria-label={'\uC218\uC815 \uC800\uC7A5'} disabled={!editingStackValue.trim()}
                            onClick={() => saveStackEdit(request.id)}><Check size={13} /></button>
                          <button type="button" aria-label={'\uC218\uC815 \uCDE8\uC18C'} onClick={cancelStackEdit}><X size={13} /></button>
                        </span>
                      ) : (
                        <button type="button" className="chat-request-stack-menu-button" aria-label={'\uB300\uAE30 \uC694\uCCAD ' + (index + 1) + ' \uBA54\uB274'}
                          aria-haspopup="menu" aria-expanded={stackMenuId === request.id}
                          onClick={(event) => openStackMenu(event, request.id)}><MoreHorizontal size={14} /></button>
                      )}
                      {stackMenuId === request.id && (
                        <div className="chat-request-stack-menu" role="menu" style={stackMenuPosition}
                          onClick={(event) => event.stopPropagation()}>
                          <button type="button" role="menuitem" onClick={() => startStackEdit(request)}>
                            <Pencil size={13} /> {'\uC218\uC815'}
                          </button>
                          <button type="button" role="menuitem" className="danger" onClick={() => removeStackRequest(request.id)}>
                            <Trash2 size={13} /> {'\uC0AD\uC81C'}
                          </button>
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              </section>
            )}
            {attachment && (
              <div className="chat-attachment-chip">
                <Paperclip size={14} />
                <span>{attachment.name}</span>
                <button
                  className="chat-attachment-remove"
                  type="button"
                  aria-label={`${attachment.name} 첨부 취소`}
                  onClick={handleAttachmentClear}
                >
                  <X size={13} />
                </button>
              </div>
            )}
            <ChatInput
              textareaRef={textareaRef}
              value={input}
              placeholder="메시지를 입력하세요. @를 입력하면 MCP tool을 선택할 수 있습니다."
              disabled={chat.isHistoryLoading || chat.resendingMessageId != null}
              onChange={handleInputChange}
              onSubmit={handleSubmit}
            />
            <div className="chat-composer-actions">
              <label
                className="chat-file-button"
                title="PDF, image, or Java source attachment"
                aria-label="파일 첨부"
              >
                <Paperclip size={17} />
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".java,.pdf,.png,.jpg,.jpeg,.bmp,.gif,.tif,.tiff,.webp,text/x-java-source,application/pdf,image/*"
                  onChange={(event) => setAttachment(event.target.files?.[0] ?? null)}
                />
              </label>
              <div className="chat-composer-controls">
              {chat.isLoading && (
                <button
                  className="chat-send-button is-stop"
                  type="button"
                  aria-label="AI 응답 요청 중지"
                  onClick={chat.stopResponse}
                >
                  <Square size={13} fill="currentColor" />
                </button>
              )}
                <button
                  className="chat-send-button"
                  type="submit"
                  aria-label="메시지 전송"
                  title={chat.isLoading ? 'Queue request' : 'Send message'}
                  disabled={chat.isHistoryLoading || chat.resendingMessageId != null
                    || (input.trim().length === 0 && !attachment)}
                >
                  <ArrowUp size={17} />
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </section>
  );
}

function formatElapsedTime(totalSeconds) {
  if (totalSeconds < 60) return `${totalSeconds}초`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}분 ${seconds}초`;
}
