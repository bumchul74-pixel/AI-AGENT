import { X } from 'lucide-react';

export function Modal({ children, className = '', onClose, open, title }) {
  if (!open) {
    return null;
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className={className ? `modal ${className}` : 'modal'} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="icon-button" type="button" onClick={onClose} aria-label="닫기">
            <X size={18} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
