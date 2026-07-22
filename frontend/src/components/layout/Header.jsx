import { Activity, ServerCog } from 'lucide-react';

const TEXT = {
  subtitle: 'RAG \uAE30\uBC18 Java \uAC1C\uBC1C \uC0DD\uC0B0\uC131 \uAD00\uB9AC \uCF58\uC194',
};

const pageTitles = {
  generate: 'Java Source Generator',
  history: 'Generation History',
  chat: 'AI Chat',
  rag: 'RAG \uC870\uD68C',
  javaGraph: 'Java Graph',
  documents: 'Document \uAD00\uB9AC',
  dataCleanup: 'Integrated Data Cleanup',
};

export function Header({ activePage = 'generate' }) {
  return (
    <header className="topbar">
      <div>
        <span className="eyebrow">AI-AGENT CONSOLE</span>
        <h2>{pageTitles[activePage] ?? 'AI Agent'}</h2>
        <p>{TEXT.subtitle}</p>
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
