import '@fontsource-variable/hanken-grotesk';
import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import App from './App';
import { dataMode } from './backend/config';
import AuthProvider from './features/auth/AuthProvider';
import { keycloakConfig } from './features/auth/config';
import { createDemoAuthClient, loadDemoUser } from './features/auth/demoAuth';
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
  renderApp(root);
}

async function renderKeycloakPage(target: Root, kcContext: KcContext) {
  await followKeycloakLanguage(i18n, kcContext.locale?.currentLanguageTag);
  target.render(
    <StrictMode>
      <KcPage kcContext={kcContext} />
    </StrictMode>,
  );
}

function renderApp(target: Root) {
  const mode = dataMode();
  // One auth client for the whole app, created outside React so StrictMode cannot create two.
  // Demo mode swaps Keycloak for synthetic personas and the API for in-memory data.
  const authClient =
    mode === 'demo' ? createDemoAuthClient() : createKeycloakClient(keycloakConfig);
  target.render(
    <StrictMode>
      <AuthProvider client={authClient} loadUser={mode === 'demo' ? loadDemoUser : undefined}>
        <App mode={mode} />
      </AuthProvider>
    </StrictMode>,
  );
}
