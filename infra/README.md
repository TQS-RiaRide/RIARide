# Infrastructure

Docker Compose for the local development stack. For now it runs PostgreSQL and Keycloak; the other services join as their stories land (architecture, section 4).

## Start

```sh
cp infra/.env.example infra/.env        # set every password; infra/.env is git-ignored
docker compose -f infra/compose.yaml up -d
infra/keycloak/create-dev-users.sh      # synthetic users and a dev-only client, idempotent
```

| Service | Address | Notes |
|---|---|---|
| PostgreSQL | `localhost:5432` | Databases `riaride` (backend, owner `riaride`) and `keycloak` |
| Keycloak | `http://localhost:8180` | Realm `riaride`; admin console with `KEYCLOAK_ADMIN_*` from `.env` |

Ports bind to `127.0.0.1` only. Set `POSTGRES_PORT` or `KEYCLOAK_PORT` in `.env` if the defaults are taken; then point the backend at them with `DB_URL` and `KEYCLOAK_ISSUER_URI`.

## Keycloak

- `keycloak/riaride-realm.json` is the realm: roles `GUEST`, `OPERATOR`, `TECHNICIAN`, `HOTEL_MANAGER`, `ADMIN`; the public client `riaride-spa` (Authorization Code with PKCE); a user profile with the admin-only attribute `hotel_id`, which the token carries as the `hotel_id` claim. It contains no users and no secrets. Keycloak imports it on start only if the realm does not exist; to re-import, remove the volume (`docker compose -f infra/compose.yaml down -v`).
- The backend's acceptance tests (Cucumber) import the same file into a Keycloak test container.
- `keycloak/create-dev-users.sh` creates one manager per Marés hotel, an operator, a technician, an admin and a guest, all with `DEV_USER_PASSWORD`, plus the client `riaride-dev-cli`, which allows the password grant for local testing. Run it only against the local stack.

Hotel ids are fixed by the backend's Flyway migration `V1__accounts_hotel_and_stay.sql`:

| Hotel | `hotel_id` |
|---|---|
| Marés Centro | `00000000-0000-0000-0000-000000000001` |
| Marés Praia | `00000000-0000-0000-0000-000000000002` |
| Marés Salinas | `00000000-0000-0000-0000-000000000003` |
