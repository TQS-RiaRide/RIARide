# RiaRide frontend

Walking skeleton of the RiaRide web UI, following [ADR 0005](../docs/architecture/adr/0005-react-frontend.md): React, Vite and TypeScript, with react-i18next for Portuguese and English (texts in `src/locales/*.json`) and Vitest for unit tests. It shows whether the backend is reachable and switches language.

## Commands

Requires Node.js 22.22.2 or later (24 LTS recommended), as declared in `package.json`.

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

## Backend contract

`GET /api/v1/health` is mapped to `GET /actuator/health` on the backend, in `vite.config.ts` for development and in `nginx.conf` in the container. The backend needs `spring-boot-starter-actuator`. Other `/api/v1/*` calls are forwarded unchanged to `backend:8080`.

## Next steps

- Generate the API client from the backend's OpenAPI document.
- Log in with Keycloak through keycloak-js (Authorization Code with PKCE, [ADR 0009](../docs/architecture/adr/0009-keycloak.md)), and route `/auth` in nginx.
