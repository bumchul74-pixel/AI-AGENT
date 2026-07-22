import { ChevronRight } from 'lucide-react';
import { findNavigationSection } from '../../constants/navigation.js';

export function ContextNavigation({ activePage, onNavigate }) {
  const section = findNavigationSection(activePage);

  if (!section) {
    return null;
  }

  return (
    <section className="context-navigation" aria-label={`${section.label} 하위 메뉴`}>
      <div className="context-navigation-summary">
        <span className="context-navigation-kicker">CURRENT SPACE</span>
        <strong>{section.label}</strong>
        <small>{section.description}</small>
      </div>
      <ChevronRight className="context-navigation-divider" size={18} aria-hidden="true" />
      <nav className="context-navigation-tabs">
        {section.children.map((item) => {
          const isActive = activePage === item.id;
          return (
            <button
              aria-current={isActive ? 'page' : undefined}
              className={isActive ? 'context-navigation-tab active' : 'context-navigation-tab'}
              key={item.id}
              type="button"
              onClick={() => onNavigate(item.id)}
            >
              <span className="context-navigation-tab-icon"><item.icon size={18} /></span>
              <span>
                <strong>{item.label}</strong>
                <small>{item.description}</small>
              </span>
            </button>
          );
        })}
      </nav>
    </section>
  );
}
