import { useEffect, useRef } from 'react';
import { AlertCircle, X } from 'lucide-react';

export function Toast({ message, onClose, duration = 5000 }) {
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!message) return undefined;

    const timer = window.setTimeout(() => onCloseRef.current?.(), duration);
    return () => window.clearTimeout(timer);
  }, [duration, message]);

  if (!message) return null;

  return (
    <div className="toast-region" aria-live="polite" aria-atomic="true">
      <div className="toast toast-error" role="alert">
        <AlertCircle size={18} aria-hidden="true" />
        <span>{message}</span>
        <button type="button" onClick={onClose} aria-label="알림 닫기">
          <X size={16} aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
