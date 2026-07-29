import { useEffect, useState } from 'react';
import { ChevronDown, LayoutDashboard } from 'lucide-react';
import { findNavigationSection, NAVIGATION_SECTIONS } from '../../constants/navigation.js';

export function Sidebar({ activePage, collapsed, onNavigate }) {
  const activeSection = findNavigationSection(activePage);
  const [expandedSections, setExpandedSections] = useState(
    () => new Set(activeSection ? [activeSection.id] : []),
  );

  useEffect(() => {
    if (!activeSection) return;
    setExpandedSections((current) => {
      if (current.has(activeSection.id)) return current;
      return new Set([...current, activeSection.id]);
    });
  }, [activeSection?.id]);

  function toggleSection(sectionId) {
    if (collapsed) return;
    setExpandedSections((current) => {
      const next = new Set(current);
      if (next.has(sectionId)) next.delete(sectionId);
      else next.add(sectionId);
      return next;
    });
  }

  return (
    <aside
      className={collapsed ? 'sidebar collapsed' : 'sidebar'}
      id="primary-navigation"
      aria-label="주 메뉴"
    >
      <button
        className="sidebar-brand"
        type="button"
        onClick={() => onNavigate('dashboard')}
        aria-label="Dashboard로 이동"
      >
        <div className="brand-mark">AIP</div>
        <div className="sidebar-brand-copy">
          <strong>AIP</strong>
          <span>AI Integration Platform</span>
        </div>
      </button>

      <nav className="sidebar-nav" aria-label="Global navigation">
        <button
          aria-current={activePage === 'dashboard' ? 'page' : undefined}
          className={activePage === 'dashboard' ? 'gnb-dashboard active' : 'gnb-dashboard'}
          type="button"
          title={collapsed ? 'Dashboard' : undefined}
          onClick={() => onNavigate('dashboard')}
        >
          <LayoutDashboard size={19} />
          <span>Dashboard</span>
        </button>

        <div className="gnb-divider" />

        {NAVIGATION_SECTIONS.map((section) => {
          const isActive = activeSection?.id === section.id;
          const isExpanded = expandedSections.has(section.id) && !collapsed;
          return (
            <section className={isActive ? 'gnb-group active' : 'gnb-group'} key={section.id}>
              <button
                aria-expanded={isExpanded}
                className="gnb-depth1"
                type="button"
                title={collapsed ? section.label : undefined}
                onClick={() => collapsed
                  ? onNavigate(section.defaultPage)
                  : toggleSection(section.id)}
              >
                <span className="gnb-depth1-icon"><section.icon size={19} /></span>
                <span className="gnb-depth1-copy">
                  <strong>{section.label}</strong>
                  <small>{section.description}</small>
                </span>
                <ChevronDown className="gnb-expand-icon" size={17} />
              </button>

              {isExpanded && (
                <div className="gnb-depth2" aria-label={`${section.label} 하위 메뉴`}>
                  {section.children.map((item) => (
                    <button
                      aria-current={activePage === item.id ? 'page' : undefined}
                      className={activePage === item.id ? 'gnb-depth2-item active' : 'gnb-depth2-item'}
                      key={item.id}
                      type="button"
                      onClick={() => onNavigate(item.id)}
                    >
                      <item.icon size={17} />
                      <span>
                        <strong>{item.label}</strong>
                        <small>{item.description}</small>
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <span>AIP</span>
        <div><strong>AI Integration Platform</strong><small>v1.0</small></div>
      </div>
    </aside>
  );
}
