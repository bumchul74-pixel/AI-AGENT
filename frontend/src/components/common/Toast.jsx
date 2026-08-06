import { useEffect, useRef } from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, X } from 'lucide-react';

const ICONS = {
  error: AlertCircle,
  success: CheckCircle2,
  warning: AlertTriangle,
};

export function Toast({ message, variant = 'error', onClose, duration = 5000 }) {
  const onCloseRef = useRef(onClose);
  const Icon = ICONS[variant] ?? AlertCircle;

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
      <div className={`toast toast-${variant}`} role={variant === 'error' ? 'alert' : 'status'}>
        <Icon size={18} aria-hidden="true" />
        <span>{message}</span>
        <button type="button" onClick={onClose} aria-label="알림 닫기">
          <X size={16} aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
