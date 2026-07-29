import { useEffect, useRef, useState } from 'react';
import { ToastHost } from '../common/ToastHost.jsx';
import { Header } from './Header.jsx';
import { Sidebar } from './Sidebar.jsx';

const HEADER_HIDE_SCROLL_TOP = 160;
const HEADER_HIDE_DISTANCE = 48;
const HEADER_SHOW_DISTANCE = 32;
const HEADER_TRANSITION_LOCK_MS = 240;
const MOBILE_NAVIGATION_QUERY = '(max-width: 960px)';

export function MainLayout({ activePage, children, onNavigate, onRefresh, refreshKey = 0 }) {
  const [navigationOpen, setNavigationOpen] = useState(
    () => !window.matchMedia(MOBILE_NAVIGATION_QUERY).matches,
  );
  const [headerVisible, setHeaderVisible] = useState(true);
  const workspaceRef = useRef(null);
  const lastScrollTopRef = useRef(0);
  const scrollDistanceRef = useRef(0);
  const headerVisibleRef = useRef(true);
  const transitionLockedUntilRef = useRef(0);

  useEffect(() => {
    const mobileNavigation = window.matchMedia(MOBILE_NAVIGATION_QUERY);
    const handleViewportChange = (event) => setNavigationOpen(!event.matches);

    mobileNavigation.addEventListener('change', handleViewportChange);
    return () => mobileNavigation.removeEventListener('change', handleViewportChange);
  }, []);

  useEffect(() => {
    if (!navigationOpen) return undefined;

    function closeMobileNavigation(event) {
      if (event.key === 'Escape' && window.matchMedia(MOBILE_NAVIGATION_QUERY).matches) {
        setNavigationOpen(false);
      }
    }

    document.addEventListener('keydown', closeMobileNavigation);
    return () => document.removeEventListener('keydown', closeMobileNavigation);
  }, [navigationOpen]);

  useEffect(() => {
    lastScrollTopRef.current = 0;
    scrollDistanceRef.current = 0;
    headerVisibleRef.current = true;
    transitionLockedUntilRef.current = 0;
    setHeaderVisible(true);
    workspaceRef.current?.scrollTo({ top: 0 });
  }, [activePage]);

  function updateHeaderVisibility(nextVisible) {
    if (headerVisibleRef.current === nextVisible) return;
    headerVisibleRef.current = nextVisible;
    scrollDistanceRef.current = 0;
    transitionLockedUntilRef.current = performance.now() + HEADER_TRANSITION_LOCK_MS;
    setHeaderVisible(nextVisible);
  }

  function handleWorkspaceScroll(event) {
    const nextScrollTop = event.currentTarget.scrollTop;
    const delta = nextScrollTop - lastScrollTopRef.current;
    lastScrollTopRef.current = nextScrollTop;

    if (nextScrollTop <= 24) {
      scrollDistanceRef.current = 0;
      updateHeaderVisibility(true);
      return;
    }

    if (performance.now() < transitionLockedUntilRef.current || Math.abs(delta) <= 1) {
      return;
    }

    const directionChanged = (delta > 0 && scrollDistanceRef.current < 0)
      || (delta < 0 && scrollDistanceRef.current > 0);
    if (directionChanged) {
      scrollDistanceRef.current = 0;
    }
    scrollDistanceRef.current += delta;

    if (headerVisibleRef.current
      && nextScrollTop > HEADER_HIDE_SCROLL_TOP
      && scrollDistanceRef.current >= HEADER_HIDE_DISTANCE) {
      updateHeaderVisibility(false);
    } else if (!headerVisibleRef.current
      && scrollDistanceRef.current <= -HEADER_SHOW_DISTANCE) {
      updateHeaderVisibility(true);
    }
  }

  function handleNavigate(pageId) {
    onNavigate(pageId);
    if (window.matchMedia(MOBILE_NAVIGATION_QUERY).matches) {
      setNavigationOpen(false);
    }
  }

  return (
    <div className={navigationOpen ? 'app-shell' : 'app-shell navigation-collapsed'}>
      <Sidebar
        activePage={activePage}
        collapsed={!navigationOpen}
        onNavigate={handleNavigate}
      />
      {navigationOpen && (
        <button
          className="mobile-navigation-backdrop"
          type="button"
          aria-label="메뉴 닫기"
          onClick={() => setNavigationOpen(false)}
        />
      )}
      <div className={headerVisible ? 'workspace-shell' : 'workspace-shell header-hidden'}>
        <Header
          activePage={activePage}
          navigationOpen={navigationOpen}
          onToggleNavigation={() => setNavigationOpen((current) => !current)}
          onRefresh={onRefresh}
        />
        <ToastHost />
        <main className="workspace" ref={workspaceRef} onScroll={handleWorkspaceScroll}>
          <div className="workspace-content" key={refreshKey}>{children}</div>
        </main>
      </div>
    </div>
  );
}
