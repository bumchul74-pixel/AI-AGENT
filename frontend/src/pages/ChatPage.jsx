import { useEffect, useRef, useState } from 'react';
import { CornerDownLeft, Paperclip, Sparkles, X } from 'lucide-react';
import { ChatConversationList } from '../components/chat/ChatConversationList.jsx';
import { ChatMessage } from '../components/chat/ChatMessage.jsx';
import { useChat } from '../hooks/useChat.js';

export function ChatPage() {
  const chat = useChat();
  const [input, setInput] = useState('');
  const [attachment, setAttachment] = useState(null);
  const fileInputRef = useRef(null);
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
    chat.submit(nextInput, nextAttachment);
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
              <article className="chat-message assistant">
                <div className="chat-avatar">
                  <Sparkles size={17} />
                </div>
                <div className="chat-bubble">
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
            <label className={attachment ? 'chat-file-button selected' : 'chat-file-button'} title={attachment?.name ?? 'Attach Java source'}>
              {attachment ? <X size={18} onClick={() => setAttachment(null)} /> : <Paperclip size={18} />}
              <input
                ref={fileInputRef}
                type='file'
                accept='.java,text/x-java-source'
                onChange={(event) => setAttachment(event.target.files?.[0] ?? null)}
              />
            </label>
            <textarea
              value={input}
              placeholder="메시지를 입력하세요."
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  handleSubmit(event);
                }
              }}
            />
            <button type="submit" disabled={chat.isLoading || chat.isHistoryLoading || chat.resendingMessageId != null || input.trim().length === 0}>
              <CornerDownLeft size={18} />
              <span>Send</span>
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
