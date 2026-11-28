# 5. API for developers

The API is versioned under `/api/v1` and organized around resources (stays, guests, passes, hubs, vehicles, reservations, trips, tickets, routes, KPIs). Its OpenAPI document is served by the backend at `/v3/api-docs`, and Swagger UI at `/swagger-ui.html` (locally, `http://localhost:8080/swagger-ui.html`); both are public, and calling an endpoint from Swagger UI needs a Keycloak access token (Authorize). `OpenApiDocumentationIT` fails when an endpoint is missing from the document. Errors use RFC 9457 Problem Details, and a 400 names each invalid field in `invalid-params`.

Implemented so far, on `dev`:

| Method and path | Role | What it does | Story |
|---|---|---|---|
| `POST /api/v1/stays` | Hotel manager, admin | Registers a stay and returns its stay code | 1.1 (RIA-50) |
| `POST /api/v1/guests` | Public | Signs a guest up with an email, a password and a stay code | 1.2 (RIA-51) |
| `POST /api/v1/passes` | Guest | Buys a pass, charging its price and the deposit | 2.1 (RIA-52) |
| `GET /api/v1/hubs` | Any platform role | Lists every hub by name, with its hotel, capacity, docked bikes and free slots | RIA-107 |
| `POST /api/v1/hubs` | Operator, admin | Registers a hub with its capacity | 6.1 (RIA-56) |
| `POST /api/v1/hubs/{hubId}/vehicles` | Operator, admin | Registers a bike at a hub with a free slot | 6.2 (RIA-57) |
| `GET /api/v1/hubs/{hubId}/vehicles?type=` | Guest, operator, admin | Lists the vehicles at a hub, optionally of one type, each marked bookable | 3.1 and 9.2 (RIA-53, RIA-59) |
| `PUT /api/v1/vehicles/{id}/status` | Operator, admin | Activates (`AVAILABLE`), deactivates (`MAINTENANCE`) or retires (`RETIRED`) a bike; a retired bike leaves its hub, and a change the lifecycle does not allow, such as retiring a bike in use, is a 409 | 6.3 (RIA-91) |
| `GET /api/v1/passes/current` | Guest | Reads the guest's open pass, with its activation, expiry and the end of the stay; 404 when the guest holds none | RIA-107 |
| `POST /api/v1/trips` | Guest | Starts a trip on a bike now; the trip starts when telemetry confirms the unlock | 3.2 (RIA-54) |
| `GET /api/v1/trips` | Guest | Lists the guest's own trips, active and past, newest first (at most 100) | RIA-107 |
| `GET /api/v1/trips/{id}` | Guest | Reads one of the guest's own trips | 3.2 (RIA-54) |
| `POST /api/v1/trips/{id}/end` | Guest | Ends the guest's trip at a hub with a free slot | 3.4 (RIA-55) |
| `GET /api/v1/me` | Any logged-in user | Returns the user, their roles and the workspaces of the web app their roles may open | 1.3 (RIA-86) |

Every endpoint except guest sign-up needs an access token from Keycloak. A request without a valid token gets 401, and a role that may not call an endpoint gets 403, before the request body is read; both are problem details. `RoleAccessMatrixIT` checks every endpoint against every role with real Keycloak tokens, and fails when a new endpoint is not in its matrix.

Workspaces per role (`GET /api/v1/me`, RIA-86):

| Role | Workspaces, in menu order |
|---|---|
| Guest | `GUEST_DASHBOARD` |
| Operator | `OPERATOR_DASHBOARD`, `MAINTENANCE_TICKETS` |
| Technician | `MAINTENANCE_TICKETS` |
| Hotel manager | `HOTEL_BACKOFFICE` |
| Admin | `OPERATOR_DASHBOARD`, `MAINTENANCE_TICKETS`, `HOTEL_BACKOFFICE`, `ACCOUNT_ADMINISTRATION` |
