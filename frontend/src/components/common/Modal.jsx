import { useEffect } from 'react';
import { X } from 'lucide-react';

export function Modal({ backdropClassName = '', children, className = '', onClose, open, title }) {
  useEffect(() => {
    if (!open) {
      return undefined;
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        onClose();
      }
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose, open]);

  if (!open) {
    return null;
  }

  return (
    <div
      className={backdropClassName ? `modal-backdrop ${backdropClassName}` : 'modal-backdrop'}
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className={className ? `modal ${className}` : 'modal'}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="icon-button" type="button" onClick={onClose} aria-label="\uB2EB\uAE30">
            <X size={18} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
