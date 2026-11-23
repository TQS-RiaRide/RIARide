# Infrastructure

Docker Compose for the local development stack. For now it runs PostgreSQL, Keycloak, Mosquitto and the fleet simulator; the other services join as their stories land (architecture, section 4).

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
| Mosquitto | `localhost:1883` | Anonymous, local development only; watch with `docker compose -f infra/compose.yaml exec mosquitto mosquitto_sub -t 'riaride/#' -v` |
| Fleet simulator | none | Publishes the demo fleet over MQTT ([`simulator/README.md`](../simulator/README.md)) |

Ports bind to `127.0.0.1` only. Set `POSTGRES_PORT`, `KEYCLOAK_PORT` or `MQTT_PORT` in `.env` if the defaults are taken; then point the backend at them with `DB_URL` and `KEYCLOAK_ISSUER_URI`.

## Keycloak

- `keycloak/riaride-realm.json` is the realm: roles `GUEST`, `OPERATOR`, `TECHNICIAN`, `HOTEL_MANAGER`, `ADMIN`; the public client `riaride-spa` (Authorization Code with PKCE); a user profile with the admin-only attribute `hotel_id`, which the token carries as the `hotel_id` claim. It contains no users and no secrets, except the service account of `riaride-backend` (below). Keycloak imports it on start only if the realm does not exist; to re-import, remove the volume (`docker compose -f infra/compose.yaml down -v`).
- `riaride-backend` is the backend's confidential service client for the Admin API (ADR 0010): guest sign-up (RIA-51) creates and, when the local insert fails, deletes Keycloak users. Its service account has only the `realm-management` roles `manage-users` and `view-users`. Keycloak generates its secret when it imports the realm; copy it from the admin console (*Clients → riaride-backend → Credentials*) into `KEYCLOAK_BACKEND_CLIENT_SECRET` in the environment the backend runs in. `KEYCLOAK_ADMIN_URL` sets the Keycloak address the backend calls (default `http://localhost:8180`).
- The backend's acceptance and integration tests import the same file into one Keycloak test container per build and read the generated secret from it.
- `keycloak/create-dev-users.sh` creates one manager per Marés hotel, an operator, a technician, an admin and a guest, all with `DEV_USER_PASSWORD`, plus the client `riaride-dev-cli`, which allows the password grant for local testing. It refuses to run when Docker points at a remote daemon, and passes passwords through stdin or the environment (`KC_CLI_PASSWORD` for the admin login), never as command arguments.

Hotel ids are fixed by the backend's Flyway migration `V1__accounts_hotel_and_stay.sql`:

| Hotel | `hotel_id` |
|---|---|
| Marés Centro | `00000000-0000-0000-0000-000000000001` |
| Marés Praia | `00000000-0000-0000-0000-000000000002` |
| Marés Salinas | `00000000-0000-0000-0000-000000000003` |
