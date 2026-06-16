import { useState } from 'react';
import { CornerDownLeft, Sparkles } from 'lucide-react';
import { ChatMessage } from '../components/chat/ChatMessage.jsx';
import { useChat } from '../hooks/useChat.js';

const promptExamples = [
  'User 업무용 Controller, Service, DTO 구조를 추천해줘.',
  '현재 generation feature 구조에서 보완할 점을 알려줘.',
  'RAG 검색 결과를 기반으로 Mapper 생성 규칙을 정리해줘.',
];

export function ChatPage() {
  const chat = useChat();
  const [input, setInput] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    const nextInput = input;
    setInput('');
    chat.submit(nextInput);
  }

  return (
    <section className="chat-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">LLM CHAT</span>
          <h1>프로젝트 컨텍스트 기반 질의</h1>
          <p>Spring Boot 구조, RAG 검색 결과, 코드 생성 규칙을 같은 화면에서 질의합니다.</p>
        </div>
        <div className="status-badge">
          <Sparkles size={16} />
          <span>OpenAI / RAG ready</span>
        </div>
      </div>

      <div className="chat-layout">
        <aside className="card chat-context-panel">
          <h2>Prompt Examples</h2>
          <div className="prompt-example-list">
            {promptExamples.map((prompt) => (
              <button key={prompt} type="button" onClick={() => setInput(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </aside>

        <div className="card chat-thread-panel">
          <div className="chat-thread">
            {chat.messages.map((message) => (
              <ChatMessage key={message.id} message={message} />
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
            <button type="submit" disabled={chat.isLoading || input.trim().length === 0}>
              <CornerDownLeft size={18} />
              <span>Send</span>
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
