import { Header } from './Header.jsx';
import { Sidebar } from './Sidebar.jsx';

export function MainLayout({ activePage, children, onNavigate }) {
  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} onNavigate={onNavigate} />
      <div className="workspace-shell">
        <Header activePage={activePage} />
        <main className="workspace">{children}</main>
      </div>
    </div>
  );
}
