import { ChevronDown, ChevronUp } from 'lucide-react';

export function ChatNavigator({ activeIndex, messages, onNavigate }) {
  if (messages.length < 3) return null;

  const currentIndex = Math.min(Math.max(activeIndex, 0), messages.length - 1);

  return (
    <aside className="chat-navigator" aria-label="대화 내 요청 이동">
      <button
        className="chat-navigator-control"
        type="button"
        aria-label="이전 사용자 요청으로 이동"
        disabled={currentIndex === 0}
        onClick={() => onNavigate(currentIndex - 1)}
      >
        <ChevronUp size={14} />
      </button>

      <div className="chat-navigator-track" aria-label="사용자 요청 목록">
        {messages.map((message, index) => {
          const preview = createPreview(message.content);
          return (
            <button
              className={index === currentIndex
                ? 'chat-navigator-marker active'
                : 'chat-navigator-marker'}
              type="button"
              key={message.id}
              aria-current={index === currentIndex ? 'location' : undefined}
              aria-label={`${index + 1}번째 요청으로 이동: ${preview}`}
              data-preview={preview}
              onClick={() => onNavigate(index)}
            />
          );
        })}
      </div>

      <span className="chat-navigator-count">
        {currentIndex + 1}/{messages.length}
      </span>
      <button
        className="chat-navigator-control"
        type="button"
        aria-label="다음 사용자 요청으로 이동"
        disabled={currentIndex === messages.length - 1}
        onClick={() => onNavigate(currentIndex + 1)}
      >
        <ChevronDown size={14} />
      </button>
    </aside>
  );
}

function createPreview(content = '') {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length > 48 ? `${normalized.slice(0, 48)}…` : normalized;
}
