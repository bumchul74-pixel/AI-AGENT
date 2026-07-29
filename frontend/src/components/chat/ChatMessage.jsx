import { Blocks, Bot, Repeat2, UserRound } from 'lucide-react';
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
          <strong>{isUser ? 'You' : 'AIP'}</strong>
          {!isUser && message.mcpContextApplied && (
            <span className="chat-mcp-badge" title="MCP 결과를 참고해 생성된 답변입니다.">
              <Blocks size={11} />
              MCP 참고
            </span>
          )}
          <span>{formatDateTime(message.createdAt)}</span>
        </div>
        {message.attachmentName && (
          <span className='chat-message-attachment'>
            첨부파일: {message.attachmentName}
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
