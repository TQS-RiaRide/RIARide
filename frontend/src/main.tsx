import '@fontsource-variable/hanken-grotesk';
import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import App from './App';
import { dataMode } from './backend/config';
import AuthProvider from './features/auth/AuthProvider';
import { keycloakConfig } from './features/auth/config';
import { createKeycloakClient } from './features/auth/keycloakClient';
import i18n from './i18n';
import { KcPage, type KcContext } from './keycloak-theme/kc.gen';
import { followKeycloakLanguage } from './keycloak-theme/login/language';
import './styles/tokens.css';
import './styles/base.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Missing #root element in index.html');
}
const root = createRoot(container);

// The same bundle draws Keycloak's login pages, inside Keycloak, with the app's components.
if (window.kcContext) {
  void renderKeycloakPage(root, window.kcContext);
} else {
  void renderApp(root);
}

async function renderKeycloakPage(target: Root, kcContext: KcContext) {
  await followKeycloakLanguage(i18n, kcContext.locale?.currentLanguageTag);
  target.render(
    <StrictMode>
      <KcPage kcContext={kcContext} />
    </StrictMode>,
  );
}

async function renderApp(target: Root) {
  // One auth client for the whole app, created outside React so StrictMode cannot create two.
  // import.meta.env.PROD is a constant in a build, so Vite drops the demo branch and its chunks.
  if (import.meta.env.PROD || dataMode() === 'live') {
    target.render(
      <StrictMode>
        <AuthProvider client={createKeycloakClient(keycloakConfig)}>
          <App />
        </AuthProvider>
      </StrictMode>,
    );
    return;
  }
  // Demo mode swaps Keycloak for synthetic personas and the API for in-memory data. Both are
  // loaded only here: production builds are always live, so Vite leaves them out.
  const [{ createDemoAuthClient, loadDemoUser }, { createDemoBackend }] = await Promise.all([
    import('./features/auth/demoAuth'),
    import('./backend/demoBackend'),
  ]);
  target.render(
    <StrictMode>
      <AuthProvider client={createDemoAuthClient()} loadUser={loadDemoUser}>
        <App backend={createDemoBackend()} />
      </AuthProvider>
    </StrictMode>,
  );
}
