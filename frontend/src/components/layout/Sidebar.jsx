import { Bot, FileStack, MessageSquareText, ScrollText } from 'lucide-react';

const items = [
  { id: 'generate', label: 'Generate', icon: Bot },
  { id: 'chat', label: 'Chat', icon: MessageSquareText },
  { id: 'documents', label: 'Documents', icon: FileStack },
  { id: 'history', label: 'History', icon: ScrollText },
];

export function Sidebar({ activePage, onNavigate }) {
  return (
    <aside className="sidebar">
      <div className="brand-mark">AI</div>
      <nav>
        {items.map((item) => (
          <button
            className={activePage === item.id ? 'nav-item active' : 'nav-item'}
            key={item.id}
            type="button"
            onClick={() => onNavigate(item.id)}
          >
            <item.icon size={18} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
    </aside>
  );
}
