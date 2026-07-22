import { Header } from './Header.jsx';
import { Sidebar } from './Sidebar.jsx';

export function MainLayout({ activePage, children, onNavigate }) {
  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} onNavigate={onNavigate} />
      <div className="workspace-shell">
        <Header activePage={activePage} onNavigate={onNavigate} />
        <main className="workspace">
          <div className="workspace-content">{children}</div>
        </main>
      </div>
    </div>
  );
}
