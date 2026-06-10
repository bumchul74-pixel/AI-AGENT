export function Button({ children, disabled = false, icon: Icon, onClick, variant = 'primary' }) {
  return (
    <button className={`button ${variant}`} disabled={disabled} type="button" onClick={onClick}>
      {Icon && <Icon size={17} aria-hidden="true" />}
      <span>{children}</span>
    </button>
  );
}
