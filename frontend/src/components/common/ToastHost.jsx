import { useEffect, useRef, useState } from 'react';
import { APP_NOTIFICATION_EVENT } from '../../api/apiClient.js';
import { Toast } from './Toast.jsx';

export function ToastHost() {
  const [messages, setMessages] = useState([]);
  const lastToastRef = useRef({ message: '', createdAt: 0 });

  useEffect(() => {
    function handleNotification(event) {
      const message = event.detail?.message;
      const variant = event.detail?.variant ?? 'error';
      if (!message) return;

      const now = Date.now();
      const lastToast = lastToastRef.current;
      if (lastToast.message === message && lastToast.variant === variant && now - lastToast.createdAt < 1000) return;
      lastToastRef.current = { message, variant, createdAt: now };
      setMessages((current) => [...current, { message, variant }]);
    }

    window.addEventListener(APP_NOTIFICATION_EVENT, handleNotification);
    return () => window.removeEventListener(APP_NOTIFICATION_EVENT, handleNotification);
  }, []);

  return (
    <Toast
      message={messages[0]?.message || ''}
      variant={messages[0]?.variant}
      onClose={() => setMessages((current) => current.slice(1))}
    />
  );
}
