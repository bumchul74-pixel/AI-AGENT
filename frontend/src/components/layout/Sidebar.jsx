import { Bot, DatabaseZap, FileStack, GitFork, MessageSquareText, ScrollText } from 'lucide-react';

const items = [
  { id: 'generate', label: 'Generate', description: '\uC18C\uC2A4 \uC0DD\uC131', icon: Bot },
  { id: 'chat', label: 'Chat', description: 'AI \uC9C8\uC758', icon: MessageSquareText },
  { id: 'rag', label: 'RAG \uC870\uD68C', description: 'VectorDB \uAC80\uC0C9', icon: DatabaseZap },
  { id: 'javaGraph', label: 'Java Graph', description: 'Neo4j Graph', icon: GitFork },
  { id: 'documents', label: 'Documents', description: '\uD45C\uC900 \uBB38\uC11C', icon: FileStack },
  { id: 'history', label: 'History', description: '\uC0DD\uC131 \uC774\uB825', icon: ScrollText },
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