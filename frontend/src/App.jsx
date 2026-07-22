import { AppRoutes } from './routes/AppRoutes.jsx';
import { ToastHost } from './components/common/ToastHost.jsx';

export default function App() {
  return (
    <>
      <ToastHost />
      <AppRoutes />
    </>
  );
}
