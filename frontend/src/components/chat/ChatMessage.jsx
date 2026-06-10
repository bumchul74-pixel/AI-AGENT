import { Bot, UserRound } from 'lucide-react';
import { formatDateTime } from '../../utils/dateUtils.js';

export function ChatMessage({ message }) {
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
        <p>{message.content}</p>
        {message.status === 'error' && <span className="chat-status">API 연결 대기</span>}
      </div>
    </article>
  );
}
