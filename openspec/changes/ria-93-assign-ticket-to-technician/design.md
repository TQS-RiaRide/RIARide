# Design: Assign a ticket to a technician (RIA-93)

## Context

RIA-92 (PR #85, which this change is stacked on) adds the `maintenance` module: `POST /api/v1/tickets` opens a ticket in `maintenance_ticket` with the status `OPEN`, and the architecture's data model already plans a `technician_id` column on that table. Users and roles live in Keycloak; only `accounts` talks to the Keycloak Admin API (`KeycloakIdentityProvider`, used for guest sign-up), with the backend's service account, which has `view-users`.

## Goals / Non-Goals

**Goals:**
- Check the technician against Keycloak at assignment time, so a role given or removed by an admin counts at once.
- Keep `maintenance` independent of Keycloak: it asks `accounts` through its public API.

**Non-Goals:**
- No copy of the technicians in the database, and no cache of their roles.
- No technician name in the responses: the frontend already knows who is logged in, and the operator's ticket list (RIA-120) can resolve names later.

## Decisions

**The technician is the Keycloak user id.** `maintenance_ticket` gains `technician_id varchar(36)` (the same type as `guest.keycloak_id`) and `assigned_at timestamptz`, both null until the first assignment, in one Flyway migration that takes the next free version. *Alternative:* a `technician` table. Rejected by the Product Owner: it would have to follow every role change in Keycloak.

**`accounts` answers "is this user a technician?".** A new public interface in `com.riaride.accounts`, for example `TechnicianDirectory.isTechnician(String userId)`, implemented in `accounts.internal.keycloak` with the Admin API: read the user's realm role mappings (`GET /admin/realms/{realm}/users/{id}/role-mappings/realm`). A 404 from Keycloak means no such user, so not a technician. Any other failure is reported as the identity provider being unavailable, as guest sign-up already does, and becomes a 503. `maintenance` → `accounts` is a new module dependency; `accounts` does not depend on `maintenance`, so there is no cycle. *Alternative:* trust the `technicianId` sent by the operator. Rejected: it would accept anyone.

**Endpoints.**
- `PUT /api/v1/tickets/{id}/technician`, body `{"technicianId": "<user id>"}`, `OPERATOR` or `ADMIN`. `PUT` because assigning the same technician twice gives the same result (idempotent), and it replaces the previous technician. 200 with the ticket, now with `technicianId` and `assignedAt`.
- `GET /api/v1/tickets/assigned-to-me`, `TECHNICIAN`. The user id is the token's subject, so a technician cannot read another's list. Open tickets only, ordered by `sla_due_at`, then by id so equal deadlines keep a stable order.

Both are added to `SecurityConfiguration` and to the role access matrix (`RoleAccessMatrixIT`, RIA-86), with roles checked before the body is read, as RIA-86 requires.

**Order and locking.** The service checks the technician with Keycloak first, outside the database transaction, so the ticket row is not locked during an HTTP call. It then locks the ticket (`SELECT ... FOR UPDATE`), checks that it is still open, and assigns it. Two concurrent assignments of the same ticket are serialized by the row lock; the last one wins, which is what a reassignment means.

**Errors** follow RFC 9457 with an `invalid-params` member for field errors, as in RIA-92's handler, and never repeat the submitted values: 400 malformed body or missing or malformed `technicianId`, 404 unknown ticket, 409 ticket not open, 422 not a technician (naming `technicianId`), 503 identity provider unavailable.

**Index.** A partial index on `maintenance_ticket (technician_id, sla_due_at) where status = 'OPEN'` serves the technician's list.

## Risks / Trade-offs

- [Keycloak down blocks assignments] → 503 with a clear problem detail; assigning is not urgent like starting a trip, and nothing is half-done.
- [A role removed after assignment] → the ticket keeps its technician. Removing the role does not unassign; the operator reassigns. Acceptable for the MVP.
- [The 409 rule has no test until a second status exists] → specified now; RIA-94 adds the test with the closed status (see the proposal).

## Migration Plan

One additive migration: two nullable columns and an index. Rollback is not needed for existing data, since every existing ticket stays unassigned.
