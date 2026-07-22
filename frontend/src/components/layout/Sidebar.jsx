import { ChevronRight } from 'lucide-react';
import { findNavigationSection, NAVIGATION_SECTIONS } from '../../constants/navigation.js';

export function Sidebar({ activePage, onNavigate }) {
  const activeSection = findNavigationSection(activePage);

  return (
    <aside className="sidebar">
      <button
        className="sidebar-brand"
        type="button"
        onClick={() => onNavigate('dashboard')}
        aria-label="Dashboard로 이동"
      >
        <div className="brand-mark">AI</div>
        <div>
          <strong>AI Agent</strong>
          <span>RAG Console</span>
        </div>
      </button>

      <nav className="sidebar-nav" aria-label="주요 업무 영역">
        {NAVIGATION_SECTIONS.map((item) => (
          <button
            aria-current={activeSection?.id === item.id ? 'page' : undefined}
            className={activeSection?.id === item.id ? 'nav-item active' : 'nav-item'}
            key={item.id}
            type="button"
            onClick={() => onNavigate(item.defaultPage)}
          >
            <span className="nav-item-icon"><item.icon size={18} /></span>
            <span>
              <strong>{item.label}</strong>
              <small>{item.description}</small>
            </span>
            <ChevronRight className="nav-item-chevron" size={16} aria-hidden="true" />
          </button>
        ))}
      </nav>
    </aside>
  );
}
