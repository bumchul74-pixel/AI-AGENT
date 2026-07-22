import { useEffect, useRef, useState } from 'react';
import { API_ERROR_EVENT } from '../../api/apiClient.js';
import { Toast } from './Toast.jsx';

export function ToastHost() {
  const [messages, setMessages] = useState([]);
  const lastToastRef = useRef({ message: '', createdAt: 0 });

  useEffect(() => {
    function handleApiError(event) {
      const message = event.detail?.message;
      if (!message) return;

      const now = Date.now();
      const lastToast = lastToastRef.current;
      if (lastToast.message === message && now - lastToast.createdAt < 1000) return;
      lastToastRef.current = { message, createdAt: now };
      setMessages((current) => [...current, message]);
    }

    window.addEventListener(API_ERROR_EVENT, handleApiError);
    return () => window.removeEventListener(API_ERROR_EVENT, handleApiError);
  }, []);

  return (
    <Toast
      message={messages[0] || ''}
      onClose={() => setMessages((current) => current.slice(1))}
    />
  );
}
