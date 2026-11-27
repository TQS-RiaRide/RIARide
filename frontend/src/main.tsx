import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import AuthProvider from './features/auth/AuthProvider';
import { keycloakConfig } from './features/auth/config';
import { createKeycloakClient } from './features/auth/keycloakClient';
import './i18n';
import './styles.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('Missing #root element in index.html');
}

// One Keycloak client for the whole app, created outside React so StrictMode cannot create two.
const authClient = createKeycloakClient(keycloakConfig);

createRoot(root).render(
  <StrictMode>
    <AuthProvider client={authClient}>
      <App />
    </AuthProvider>
  </StrictMode>,
);
