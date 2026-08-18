export function ScrollableListLayout({
  children,
  footer,
  ariaLabel,
  className = '',
  scrollClassName = '',
  onScroll,
}) {
  const rootClassName = ['card', 'scrollable-list-layout', className].filter(Boolean).join(' ');
  const bodyClassName = ['scrollable-list-body', scrollClassName].filter(Boolean).join(' ');

  return (
    <section className={rootClassName}>
      <div
        className={bodyClassName}
        onScroll={onScroll}
        tabIndex={0}
        aria-label={ariaLabel}
      >
        {children}
      </div>
      {footer && <footer className="scrollable-list-footer">{footer}</footer>}
    </section>
  );
}