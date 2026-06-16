import { Activity, ServerCog } from 'lucide-react';

const pageTitles = {
  generate: 'Java Source Generator',
  chat: 'AI Chat',
  rag: 'Rag조회',
  documents: 'Document 관리',
  history: '생성 이력',
};

export function Header({ activePage = 'generate' }) {
  return (
    <header className="topbar">
      <div>
        <span className="eyebrow">AI-AGENT CONSOLE</span>
        <h2>{pageTitles[activePage] ?? 'AI Agent'}</h2>
        <p>RAG 기반 Java 개발 생산성 관리 콘솔</p>
      </div>

      <div className="topbar-actions">
        <div className="server-pill">
          <Activity size={16} />
          <span>RAG Ready</span>
        </div>
        <div className="server-pill">
          <ServerCog size={16} />
          <span>Backend :8081</span>
        </div>
      </div>
    </header>
  );
}
