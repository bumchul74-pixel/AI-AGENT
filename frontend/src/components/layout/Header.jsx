import { ServerCog } from 'lucide-react';

export function Header() {
  return (
    <header className="topbar">
      <div>
        <span className="eyebrow">AI-AGENT</span>
        <h2>RAG 기반 Spring Boot 코드 생성</h2>
      </div>
      <div className="server-pill">
        <ServerCog size={16} />
        <span>Backend :8081</span>
      </div>
    </header>
  );
}
