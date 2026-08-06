import { useEffect, useRef, useState } from 'react';
import { ArrowUp, Paperclip, Sparkles, Square, X } from 'lucide-react';
import { ChatConversationList } from '../components/chat/ChatConversationList.jsx';
import { ChatMessage } from '../components/chat/ChatMessage.jsx';
import { useChat } from '../hooks/useChat.js';

export function ChatPage() {
  const chat = useChat();
  const [input, setInput] = useState('');
  const [attachment, setAttachment] = useState(null);
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);
  const chatThreadRef = useRef(null);

  useEffect(() => {
    const thread = chatThreadRef.current;
    if (!thread) return;
    thread.scrollTo({
      top: thread.scrollHeight,
      behavior: chat.isLoading ? 'smooth' : 'auto',
    });
  }, [chat.messages, chat.isLoading, chat.isHistoryLoading]);

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

  function handleInputChange(event) {
    setInput(event.target.value);
    event.target.style.height = 'auto';
    event.target.style.height = `${Math.min(event.target.scrollHeight, 160)}px`;
  }

  return (
    <section className="chat-page">
      <div className="chat-layout">
        <ChatConversationList
          conversations={chat.conversations}
          projects={chat.projects}
          activeConversationId={chat.activeConversationId}
          disabled={chat.isLoading || chat.isHistoryLoading || chat.isProjectLoading || chat.resendingMessageId != null}
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
          <div className="chat-thread" ref={chatThreadRef}>
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
                <div className="chat-bubble chat-loading-bubble" role="status" aria-label="AI 응답 생성 중">
                  <div className="typing-dots">
                    <span />
                    <span />
                    <span />
                  </div>
                </div>
              </article>
            )}
          </div>

          <form className="chat-composer" onSubmit={handleSubmit}>
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
            <textarea
              ref={textareaRef}
              rows={1}
              value={input}
              placeholder="메시지를 입력하세요."
              onChange={handleInputChange}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  handleSubmit(event);
                }
              }}
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
              {chat.isLoading ? (
                <button
                  className="chat-send-button is-stop"
                  type="button"
                  aria-label="AI 응답 요청 중지"
                  onClick={chat.stopResponse}
                >
                  <Square size={13} fill="currentColor" />
                </button>
              ) : (
                <button
                  className="chat-send-button"
                  type="submit"
                  aria-label="메시지 전송"
                  disabled={chat.isHistoryLoading || chat.resendingMessageId != null
                    || (input.trim().length === 0 && !attachment)}
                >
                  <ArrowUp size={17} />
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </section>
  );
}
