# RiaRide frontend

Walking skeleton of the RiaRide web UI, following [ADR 0005](../docs/architecture/adr/0005-react-frontend.md): React, Vite and TypeScript, with react-i18next for Portuguese and English (texts in `src/locales/<language>/<namespace>.json`; see [`src/i18n/README.md`](src/i18n/README.md)) and Vitest for unit tests. It logs users in with Keycloak and shows the workspaces of their role (RIA-86), shows whether the backend is reachable and switches language.

## Commands

Requires Node.js 22.22.2 or later in the 22 line, or 24.15.0 or later (24 LTS recommended), as declared in `package.json`.

```bash
npm install          # first time; commit the generated package-lock.json
npm run dev          # http://localhost:5173, /api/v1 forwarded to http://localhost:8080
npm run lint         # ESLint (typescript-eslint strict, react-hooks)
npm run format       # Prettier; npm run format:check only checks
npm test             # unit tests (Vitest + Testing Library)
npm run test:coverage                              # tests with LCOV coverage in coverage/
npx vitest run src/features/health/api.test.ts -t "unreachable"   # a single test
npm run build        # type check and production build in dist/
docker build -t riaride-frontend .
```

Set `VITE_BACKEND_URL` to point the dev server at another backend.

## Login and roles

The app logs in with Keycloak through keycloak-js: Authorization Code with PKCE (S256), tokens kept in memory only and refreshed before each use ([ADR 0009](../docs/architecture/adr/0009-keycloak.md)). After login it calls `GET /api/v1/me` and shows a menu with only the workspaces of the user's roles (operator: operator dashboard and maintenance tickets, never the hotel backoffice). The menu only decides what the UI offers; the backend checks the role on every request. In the maintenance tickets workspace, operators and admins open a ticket for a faulty bike by its code (`POST /api/v1/tickets`, RIA-92); technicians see the workspace without that form.

| Variable (build time)     | Default                 | Meaning                            |
| ------------------------- | ----------------------- | ---------------------------------- |
| `VITE_KEYCLOAK_URL`       | `http://localhost:8180` | Keycloak as the browser reaches it |
| `VITE_KEYCLOAK_REALM`     | `riaride`               | Realm                              |
| `VITE_KEYCLOAK_CLIENT_ID` | `riaride-spa`           | Public client of the web app       |

The realm lets the SPA client redirect back to `http://localhost:5173` (dev server) and `http://localhost:8081` (container). Users for local testing come from `infra/keycloak/create-dev-users.sh`.

Code: `src/features/auth` (Keycloak client, session provider, log in and log out) and `src/features/workspaces` (menu). Components depend on the `AuthClient` interface, so tests pass a fake instead of keycloak-js.

## Backend contract

`GET /api/v1/health` is mapped to `GET /actuator/health` on the backend, in `vite.config.ts` for development and in `nginx.conf` in the container. The backend needs `spring-boot-starter-actuator`. Other `/api/v1/*` calls are forwarded unchanged to `backend:8080`.

## Next steps

- Generate the API client from the backend's OpenAPI document.
- Route `/auth` in nginx, so the browser reaches Keycloak through the same origin when it is deployed (ADR 0009).
- A router, when the workspaces get pages of their own.
