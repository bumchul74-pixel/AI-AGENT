import { findNavigationSection } from '../../constants/navigation.js';

export function Header({ activePage = 'generate', onNavigate }) {
  const section = findNavigationSection(activePage);
  const activeItem = section?.children.find((item) => item.id === activePage);

  return (
    <header className="topbar">
      <div className="topbar-context">
        <span className="eyebrow">{activeItem?.label ?? 'AI-AGENT CONSOLE'}</span>
        <h2>{section?.label ?? 'AI Agent'}</h2>
        <p>{section?.description ?? 'RAG 기반 Java 개발 생산성 관리 콘솔'}</p>
      </div>

      <div className="topbar-tools">
        {section && (
          <nav className="header-subnav" aria-label={`${section.label} 하위 메뉴`}>
            {section.children.map((item) => {
              const isActive = item.id === activePage;
              return (
                <button
                  aria-current={isActive ? 'page' : undefined}
                  className={isActive ? 'header-subnav-tab active' : 'header-subnav-tab'}
                  key={item.id}
                  type="button"
                  onClick={() => onNavigate?.(item.id)}
                >
                  <item.icon size={16} aria-hidden="true" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </nav>
        )}
      </div>
    </header>
  );
}
