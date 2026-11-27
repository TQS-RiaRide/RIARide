# RiaRide frontend

The RiaRide web UI, following [ADR 0005](../docs/architecture/adr/0005-react-frontend.md): React, Vite and TypeScript, with react-i18next for Portuguese and English (texts in `src/locales/<language>/<namespace>.json`; see [`src/i18n/README.md`](src/i18n/README.md)) and Vitest for unit tests. It logs users in with Keycloak and sends each role to its own area (RIA-86): the guest app (pass, bikes by hub, trips) and the staff backoffice (fleet, maintenance tickets, hotel backoffice). React Router handles navigation, TanStack Query caches and refreshes server data, and the shell adds a command menu (⌘K / Ctrl+K) and keyboard shortcuts (`?` lists them).

## Commands

Requires Node.js 22.22.2 or later in the 22 line, or 24.15.0 or later (24 LTS recommended), as declared in `package.json`.

```bash
npm install          # first time; commit the generated package-lock.json
npm run dev          # http://localhost:5173, /api/v1 forwarded to http://localhost:8080
npm run dev:demo     # the same UI with no backend or Keycloak: sample data and synthetic roles
npm run lint         # ESLint (typescript-eslint strict, react-hooks)
npm run format       # Prettier; npm run format:check only checks
npm test             # unit tests (Vitest + Testing Library)
npm run test:coverage                              # tests with LCOV coverage in coverage/
npx vitest run src/features/health/api.test.ts -t "unreachable"   # a single test
npm run build        # type check and production build in dist/
npm run build:keycloak-theme  # the same build packed as Keycloak's login theme (needs Maven and a JDK)
docker build -t riaride-frontend .
```

Set `VITE_BACKEND_URL` to point the dev server at another backend.

## Live and demo data

Screens read the platform through one port, `src/backend/Backend.ts`, with two adapters:

- **live** (default): the REST API. Where the API has no read endpoint yet (hub list, current pass, trip history, ticket list, KPIs; RIA-107, RIA-117, RIA-118, RIA-120), it lists what this browser created or read and shows an empty state that names the story. Replacing a stopgap with a real endpoint only changes `liveBackend.ts`.
- **demo** (`npm run dev:demo`, which is `vite --mode demo`; or `VITE_DATA_MODE=demo` for the dev server): an in-memory Vila Ria with synthetic hubs, bikes, stays and tickets that follows BR6, BR10, BR12 and BR13. The sign-in page offers the synthetic personas instead of Keycloak. Nothing reaches a server. A production build (`npm run build`, the image and the Keycloak theme jar) is always live: demo mode signs anyone in as any role, so Vite leaves the demo modules out of it.

## Login and roles

The app logs in with Keycloak through keycloak-js: Authorization Code with PKCE (S256), tokens kept in memory only and refreshed before each use ([ADR 0009](../docs/architecture/adr/0009-keycloak.md)). After login it calls `GET /api/v1/me` and shows a menu with only the workspaces of the user's roles (operator: operator dashboard and maintenance tickets, never the hotel backoffice). The menu only decides what the UI offers; the backend checks the role on every request. In the maintenance tickets workspace, operators and admins open a ticket for a faulty bike by its code (`POST /api/v1/tickets`, RIA-92); technicians see the workspace without that form.

| Variable (build time)     | Default                 | Meaning                            |
| ------------------------- | ----------------------- | ---------------------------------- |
| `VITE_KEYCLOAK_URL`       | `http://localhost:8180` | Keycloak as the browser reaches it |
| `VITE_KEYCLOAK_REALM`     | `riaride`               | Realm                              |
| `VITE_KEYCLOAK_CLIENT_ID` | `riaride-spa`           | Public client of the web app       |

The realm lets the SPA client redirect back to `http://localhost:5173` (dev server) and `http://localhost:8081` (container). Users for local testing come from `infra/keycloak/create-dev-users.sh`.

### Keycloak's login pages

Keycloak's pages (log in, reset and update password, messages, errors, log out) are drawn by this app, with its own components, tokens and translations, so logging in does not look like another product. [Keycloakify](https://www.keycloakify.dev) packs the production build as the theme `riaride`, the realm's `loginTheme`; `main.tsx` renders `src/keycloak-theme` instead of the app when Keycloak gives the page a `kcContext`.

- RiaRide's own pages are in `src/keycloak-theme/login/pages`, with their text in `src/locales/<language>/login.json`. Keycloak's messages (a refused password, an email sent) come from Keycloak in the session's language. Pages RiaRide does not draw (one-time codes, terms) fall back to Keycloakify's markup with the app's classes.
- The language menu reloads the page through Keycloak, which then renders its messages in that language.
- Links back to the app use the redirect URI Keycloak accepted (its `client_data`), never the one the browser asked for.
- `npm run build:keycloak-theme` writes `dist_keycloak/riaride-keycloak-theme.jar`. Compose builds the Keycloak image with it (`infra/keycloak/Dockerfile`); after changing a page, rebuild with `docker compose -f infra/compose.yaml up -d --build keycloak`.
- Tests render each page from Keycloakify's mock contexts (`src/keycloak-theme/login/KcPage.test.tsx`).

Code: `src/features/auth` (Keycloak client, session provider, log in and log out), `src/app/routes.tsx` (pages per role) and `src/features/shell` (guest and staff layouts). Components depend on the `AuthClient` interface, so tests pass a fake instead of keycloak-js.

## Backend contract

`GET /api/v1/health` is mapped to `GET /actuator/health` on the backend, in `vite.config.ts` for development and in `nginx.conf` in the container. The backend needs `spring-boot-starter-actuator`. Other `/api/v1/*` calls are forwarded unchanged to `backend:8080`.

## Next steps

- Generate the API client from the backend's OpenAPI document.
- Route `/auth` in nginx, so the browser reaches Keycloak through the same origin when it is deployed (ADR 0009).
- Point the live adapter's stopgaps at the read endpoints as they land.
