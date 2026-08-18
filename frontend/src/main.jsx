import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './styles.css';
import './styles/theme.css';
import './styles/layout.css';
import './styles/java-graph-views.css';
import './styles/source-quality.css';
import './styles/neo4j-explorer.css';
import './styles/components.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
