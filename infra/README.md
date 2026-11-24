# Infrastructure

Docker Compose for the local development stack. By default it runs the infrastructure (PostgreSQL, Keycloak and Mosquitto), so the backend and frontend can run from the IDE. Two profiles add the rest of the platform in containers: `app` (backend and frontend) and `sim` (vehicle simulator). Monitoring joins when its story lands (architecture, section 4).

## Start

```sh
cp infra/.env.example infra/.env        # set every password; infra/.env is git-ignored
docker compose -f infra/compose.yaml up -d
infra/keycloak/create-dev-users.sh      # synthetic users and a dev-only client, idempotent
```

The whole platform in containers (builds the images first):

```sh
docker compose -f infra/compose.yaml --profile app --profile sim up -d --build
docker compose -f infra/compose.yaml ps    # every service should become "healthy"
```

Pass the same profiles to `down`, or the profile services keep running.

| Service | Address | Notes |
|---|---|---|
| PostgreSQL | `localhost:5432` | Databases `riaride` (backend, owner `riaride`) and `keycloak` |
| Keycloak | `http://localhost:8180` | Realm `riaride`; admin console with `KEYCLOAK_ADMIN_*` from `.env` |
| Mosquitto | `localhost:1883` | MQTT broker for the simulator (ADR 0006); anonymous access, local stack only. Watch with `docker compose -f infra/compose.yaml exec mosquitto mosquitto_sub -t 'riaride/#' -v` |
| Backend (`app`) | `http://localhost:8080` | Health at `/actuator/health` |
| Frontend (`app`) | `http://localhost:8081` | nginx; forwards `/api/v1` to the backend |
| Simulator (`sim`) | none | Publishes the demo fleet over MQTT to `mosquitto:1883` ([`simulator/README.md`](../simulator/README.md)) |

Ports bind to `127.0.0.1` only. Set `POSTGRES_PORT`, `KEYCLOAK_PORT`, `MQTT_PORT`, `BACKEND_PORT` or `FRONTEND_PORT` in `.env` if the defaults are taken; a backend run from the IDE then needs `DB_URL` and `KEYCLOAK_ISSUER_URI` pointed at them.

In the `app` profile the backend reaches the other services through the Compose network. Keycloak sets `KC_HOSTNAME` to `http://localhost:8180`, so tokens carry the same issuer whether the browser or the backend container asked for them. `STRIPE_SECRET_KEY` may stay empty: the backend container then starts with a placeholder test mode key, and only pass purchases fail.

The backend subscribes to vehicle telemetry from the Mosquitto broker in this stack (ADR 0006). A backend run from the IDE uses the default `MQTT_URL`, `tcp://localhost:1883`; in the `app` profile Compose sets it to `tcp://mosquitto:1883`. `MQTT_USERNAME` and `MQTT_PASSWORD` are for a broker that requires them, and `MQTT_ENABLED=false` turns the adapter off. Without a reachable broker the backend still starts and retries every 5 seconds. The session is kept per `MQTT_CLIENT_ID` (default `riaride-backend`), so run one backend per client id.

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
