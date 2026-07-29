import { useEffect, useRef, useState } from 'react';
import {
  Bell,
  Check,
  CircleHelp,
  Menu,
  Moon,
  Palette,
  RefreshCw,
  Sun,
  UserRound,
} from 'lucide-react';
import { findNavigationSection } from '../../constants/navigation.js';

const COLOR_MODE_KEY = 'aip-color-mode';
const THEME_KEY = 'aip-theme';
const THEMES = [
  { id: 'indigo', label: 'Indigo' },
  { id: 'teal', label: 'Teal' },
  { id: 'purple', label: 'Purple' },
];

function getStoredValue(key, fallback) {
  try {
    return window.localStorage.getItem(key) || fallback;
  } catch {
    return fallback;
  }
}

export function Header({
  activePage = 'generate',
  navigationOpen,
  onToggleNavigation,
  onRefresh,
}) {
  const section = findNavigationSection(activePage);
  const activeItem = section?.children.find((item) => item.id === activePage);
  const [colorMode, setColorMode] = useState(() => getStoredValue(COLOR_MODE_KEY, 'light'));
  const [theme, setTheme] = useState(() => getStoredValue(THEME_KEY, 'indigo'));
  const [themeMenuOpen, setThemeMenuOpen] = useState(false);
  const themeMenuRef = useRef(null);

  useEffect(() => {
    document.documentElement.dataset.colorMode = colorMode;
    try {
      window.localStorage.setItem(COLOR_MODE_KEY, colorMode);
    } catch {
      // The selected mode still applies for the current session.
    }
  }, [colorMode]);

  useEffect(() => {
    document.documentElement.dataset.uiTheme = theme;
    try {
      window.localStorage.setItem(THEME_KEY, theme);
    } catch {
      // The selected theme still applies for the current session.
    }
  }, [theme]);

  useEffect(() => {
    if (!themeMenuOpen) return undefined;

    function closeThemeMenu(event) {
      if (!themeMenuRef.current?.contains(event.target)) {
        setThemeMenuOpen(false);
      }
    }

    function closeThemeMenuWithKeyboard(event) {
      if (event.key === 'Escape') {
        setThemeMenuOpen(false);
      }
    }

    document.addEventListener('pointerdown', closeThemeMenu);
    document.addEventListener('keydown', closeThemeMenuWithKeyboard);
    return () => {
      document.removeEventListener('pointerdown', closeThemeMenu);
      document.removeEventListener('keydown', closeThemeMenuWithKeyboard);
    };
  }, [themeMenuOpen]);

  return (
    <header className="topbar">
      <div className="topbar-primary">
        <button
          className="topbar-icon-button topbar-menu-button"
          type="button"
          aria-label={navigationOpen ? '메뉴 닫기' : '메뉴 열기'}
          aria-controls="primary-navigation"
          aria-pressed={navigationOpen}
          onClick={onToggleNavigation}
        >
          <Menu size={21} />
        </button>
        <div className="topbar-context">
          <h1>{activeItem?.label ?? 'Dashboard'}</h1>
          <span className="topbar-breadcrumb">
            {section ? `${section.label} / ${activeItem?.label}` : 'AIP Administration'}
          </span>
        </div>
      </div>

      <div className="topbar-tools">
        <button
          className="topbar-icon-button"
          type="button"
          aria-label="현재 화면 새로고침"
          title="새로고침"
          onClick={onRefresh}
        >
          <RefreshCw size={19} />
        </button>
        <button
          className="topbar-icon-button"
          type="button"
          aria-label={colorMode === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'}
          aria-pressed={colorMode === 'dark'}
          title={colorMode === 'dark' ? '라이트 모드' : '다크 모드'}
          onClick={() => setColorMode((current) => (current === 'dark' ? 'light' : 'dark'))}
        >
          {colorMode === 'dark' ? <Sun size={19} /> : <Moon size={19} />}
        </button>
        <div className="topbar-theme-control" ref={themeMenuRef}>
          <button
            className="topbar-icon-button"
            type="button"
            aria-label="테마 변경"
            aria-expanded={themeMenuOpen}
            aria-haspopup="menu"
            title="테마 변경"
            onClick={() => setThemeMenuOpen((current) => !current)}
          >
            <Palette size={19} />
          </button>
          {themeMenuOpen && (
            <div className="topbar-theme-menu" role="menu" aria-label="색상 테마">
              <span>Theme</span>
              {THEMES.map((option) => (
                <button
                  type="button"
                  role="menuitemradio"
                  aria-checked={theme === option.id}
                  key={option.id}
                  onClick={() => {
                    setTheme(option.id);
                    setThemeMenuOpen(false);
                  }}
                >
                  <i className={`theme-swatch theme-swatch-${option.id}`} />
                  <strong>{option.label}</strong>
                  {theme === option.id && <Check size={16} />}
                </button>
              ))}
            </div>
          )}
        </div>
        <button className="topbar-icon-button topbar-optional-button" type="button" aria-label="도움말"><CircleHelp size={20} /></button>
        <button className="topbar-icon-button topbar-optional-button" type="button" aria-label="알림"><Bell size={20} /></button>
        <button className="topbar-user" type="button" aria-label="사용자 메뉴">
          <span><UserRound size={18} /></span>
          <strong>Administrator</strong>
        </button>
      </div>
    </header>
  );
}
