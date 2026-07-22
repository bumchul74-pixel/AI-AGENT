export function Button({ children, disabled = false, icon: Icon, onClick, type = 'button', variant = 'primary' }) {
  return (
    <button className={`button ${variant}`} disabled={disabled} type={type} onClick={onClick}>
      {Icon && <Icon size={17} aria-hidden="true" />}
      <span>{children}</span>
    </button>
  );
}
