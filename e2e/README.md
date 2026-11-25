# End-to-end tests

Playwright (TypeScript) drives the web UI in Chromium against the Compose stack, with axe checking accessibility (QA Manual 5.3 and 5.4). These tests cover the UI path of the core use cases; Cucumber covers the business rules. They run outside the regular build: nightly on `main`, on demand (the label `e2e` on a pull request, or *Run workflow* on a branch), and before each tag (`.github/workflows/e2e.yml`).

Each run creates its own hub and bikes through the public API, with codes unique to the run, so runs never depend on each other or on the demo data.

## Run locally

Node.js 22.22.2 or later in the 22 line, or 24.15.0 or later. From the repository root:

```bash
docker compose -f infra/compose.yaml --profile app up -d --build --wait
infra/keycloak/create-dev-users.sh
cd e2e
npm ci
npx playwright install chromium
set -a; . ../infra/.env; set +a   # DEV_USER_PASSWORD, the password of the dev users
npm test
```

The tests open the frontend container at `http://localhost:8081`, the only address besides the dev server that Keycloak lets the SPA return to, and Keycloak at `http://localhost:8180`. `E2E_BASE_URL` and `E2E_KEYCLOAK_URL` override them. The HTML report is in `playwright-report/` (`npx playwright show-report`).

| Command                                   | What it does                           |
| ----------------------------------------- | -------------------------------------- |
| `npm test`                                | Runs every test                        |
| `npx playwright test -g "retired"`        | Runs the tests whose title matches     |
| `npm run typecheck`                       | Type checks the tests                  |
| `npm run format` / `npm run format:check` | Formats / checks formatting (Prettier) |

## Tests

| File                                    | Story  | What it covers                                                                                                                     |
| --------------------------------------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| `tests/open-maintenance-ticket.spec.ts` | RIA-92 | An operator opens a ticket; a retired bike and an empty fault are refused; a technician has no form; axe in English and Portuguese |
