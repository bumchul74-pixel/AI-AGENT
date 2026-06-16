import { Bot, DatabaseZap, FileStack, MessageSquareText, ScrollText } from 'lucide-react';

const items = [
  { id: 'generate', label: 'Generate', description: '소스 생성', icon: Bot },
  { id: 'chat', label: 'Chat', description: 'AI 질의', icon: MessageSquareText },
  { id: 'rag', label: 'Rag조회', description: 'VectorDB 검색', icon: DatabaseZap },
  { id: 'documents', label: 'Documents', description: '표준 문서', icon: FileStack },
  { id: 'history', label: 'History', description: '생성 이력', icon: ScrollText },
];

export function Sidebar({ activePage, onNavigate }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="brand-mark">AI</div>
        <div>
          <strong>AI Agent</strong>
          <span>RAG Console</span>
        </div>
      </div>

      <nav className="sidebar-nav" aria-label="Main navigation">
        {items.map((item) => (
          <button
            className={activePage === item.id ? 'nav-item active' : 'nav-item'}
            key={item.id}
            type="button"
            onClick={() => onNavigate(item.id)}
          >
            <item.icon size={18} />
            <span>
              <strong>{item.label}</strong>
              <small>{item.description}</small>
            </span>
          </button>
        ))}
      </nav>
    </aside>
  );
}
