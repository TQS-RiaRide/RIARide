# 5. API for developers

> Draft for section 5 of the Product Specification report (RIA-16). The resources below are planned; the methods will be documented with OpenAPI and Swagger UI, linked here and in the README.

The REST API is versioned under `/api/v1` and organized around resources, not actions. It is documented with OpenAPI, from which the frontend client is generated. Requests are authenticated with a bearer token issued by Keycloak, and each endpoint is authorized by role. Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details.

| Resource | Module | Main users | Purpose |
|---|---|---|---|
| `/stays` | `accounts` | Hotel | Stays registered at check-in, with their stay codes |
| `/guests` | `accounts` | Guest | Registration with a stay code, profile |
| `/pass-prices` | `passes` | Hotel manager | Base price and deposit of each pass type |
| `/passes` | `passes` | Guest | Passes bought by a guest, with their state and validity |
| `/usage-rules` | `passes` | Hotel manager | Maximum trip duration, reservation hold, minimum battery, overtime price |
| `/hubs` | `fleet` | All | Hubs, capacity, free slots and the vehicles available at each one |
| `/vehicles` | `fleet` | Operator | Fleet registration and lifecycle: activate, deactivate, retire |
| `/reservations` | `trips` | Guest | Holding a vehicle for 15 min before the trip starts |
| `/trips` | `trips` | Guest | Starting and ending trips, past trips and charges |
| `/tickets` | `maintenance` | Operator, technician | Maintenance tickets with SLA deadlines and assignment |
| `/routes` | `backoffice` | Hotel manager, guest | Suggested routes as ordered lists of hubs |
| `/kpis` | `backoffice` | Hotel manager | Business KPIs and technical metrics |

The walking skeleton exposes the backend health at `/api/v1/health`, mapped to Spring Boot Actuator `/actuator/health`, which the frontend uses to show whether the backend is reachable.
