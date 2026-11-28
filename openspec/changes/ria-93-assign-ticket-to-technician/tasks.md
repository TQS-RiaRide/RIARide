# Tasks: Assign a ticket to a technician (RIA-93)

Agent flow of QA Manual 6.3: the test author writes tasks 1 and 2 (failing tests only), the implementer writes tasks 3 to 5 without changing any test, and the reviewer reviews the pull request. A human checks and commits each step.

## 1. Acceptance tests (test author)

- [ ] 1.1 Write `RIA-93-assign-ticket-to-technician.feature` from the scenarios in `specs/maintenance-ticket-assignment/spec.md`, tagged `@RIA-93`; the main scenario ("Assigned ticket appears in the technician's list") also tagged `@TEST_RIA-184`
- [ ] 1.2 Step definitions with real Keycloak users for the operator, admin and technicians (`KeycloakTestUsers`), REST Assured against the API

## 2. Unit and integration tests (test author)

- [ ] 2.1 Domain: an open ticket is assigned and reassigned, keeps `OPEN`, records the technician and the time from an injected clock
- [ ] 2.2 Service: a user who is not a technician is refused and the ticket is unchanged; the identity provider being unavailable gives the 503 exception and no change; Keycloak is called before the ticket is locked
- [ ] 2.3 `accounts`: the technician check against the Keycloak Admin API, with WireMock or the Keycloak container: technician, user without the role, unknown user (404), Keycloak error
- [ ] 2.4 API integration tests with PostgreSQL in Testcontainers: 200, 400 (missing or malformed `technicianId`, body that is not JSON), 404, 422, 503; the list ordered by deadline and limited to the caller's open tickets
- [ ] 2.5 Add both endpoints to `RoleAccessMatrixIT`: allowed roles, 403 for the others, 401 without a token
- [ ] 2.6 Migration integration test: the new columns are null for tickets opened before it

## 3. Persistence (implementer)

- [ ] 3.1 Flyway migration with the next free version: `technician_id`, `assigned_at` and the partial index
- [ ] 3.2 Repository queries: lock a ticket by id; open tickets of a technician by deadline

## 4. Modules and API (implementer)

- [ ] 4.1 `accounts` public technician check and its Keycloak Admin API implementation
- [ ] 4.2 `maintenance` assignment service, the two endpoints, the problem details and the security rules

## 5. Documentation (implementer)

- [ ] 5.1 Product Specification API section: the two endpoints, their roles and errors
- [ ] 5.2 Architecture: the `maintenance` → `accounts` dependency and the new columns in the data model
