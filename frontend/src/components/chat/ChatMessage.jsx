import { Bot, Repeat2, UserRound } from 'lucide-react';
import { formatDateTime } from '../../utils/dateUtils.js';

export function ChatMessage({ message, onResend, resendDisabled, isResending }) {
  const isUser = message.role === 'user';
  const Icon = isUser ? UserRound : Bot;

  return (
    <article className={isUser ? 'chat-message user' : 'chat-message assistant'}>
      <div className="chat-avatar">
        <Icon size={17} />
      </div>
      <div className="chat-bubble">
        <div className="chat-meta">
          <strong>{isUser ? 'You' : 'AI-AGENT'}</strong>
          <span>{formatDateTime(message.createdAt)}</span>
        </div>
        {message.attachmentName && (
          <span className='chat-message-attachment'>
            Attached: {message.attachmentName}
          </span>
        )}
        <p>{message.content}</p>
        {isUser && onResend && (
          <button
            className="chat-resend-button"
            type="button"
            disabled={resendDisabled}
            onClick={() => onResend(message)}
          >
            <Repeat2 size={14} />
            <span>{isResending ? '재전송 중...' : '재전송'}</span>
          </button>
        )}
        {message.status === 'error' && <span className="chat-status">API 연결 대기</span>}
      </div>
    </article>
  );
}
