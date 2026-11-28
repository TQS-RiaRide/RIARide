# Proposal: Assign a ticket to a technician (RIA-93)

## Why

RIA-92 lets an operator open a maintenance ticket with an SLA deadline, but nobody owns the repair yet. RodaLeve shares the work among its technicians, so an operator must be able to give each ticket to one of them, and each technician must see the tickets that are theirs. RIA-94 (close a ticket) and the SLA alert (RIA-95) build on this.

This is the second story of AI4 (QA Manual 6.5): agents generate it from this proposal, through the flow of QA Manual 6.3, once the team has reviewed it.

## What Changes

- An operator or an admin assigns an open ticket to a technician, or reassigns it to another one.
- A technician is a user of the platform's Keycloak realm with the `TECHNICIAN` role. The backend checks this at assignment time; there is no separate list of technicians to keep in sync.
- A technician lists the open tickets assigned to them, earliest deadline first.
- Assigning does not change the ticket's status: it stays `OPEN`. The statuses that follow (for example, closed) come with RIA-94; a ticket that is no longer open cannot be assigned.

Product Owner decisions (28 November 2026), taken before generation, as QA Manual 6.5 asks:

| Question | Decision |
|---|---|
| Who assigns a ticket | Operator and admin, the roles that open tickets |
| How a technician is identified | A Keycloak user with the `TECHNICIAN` realm role, checked through the Keycloak Admin API |
| Whether the technician's list is part of this story | Yes: the acceptance criterion checks it, and RIA-94 needs it |
| Reassignment and status | An open ticket can be assigned and reassigned; its status stays `OPEN`. New statuses are RIA-94 |

## Capabilities

### New Capabilities

- `maintenance-ticket-assignment`: giving an open maintenance ticket to a technician, and the technician's list of the tickets assigned to them.

### Modified Capabilities

None. Opening a ticket (RIA-92) is not changed, and it has no spec in `openspec/specs/` yet.

## Impact

- **API:** `PUT /api/v1/tickets/{id}/technician` (operator, admin) and `GET /api/v1/tickets/assigned-to-me` (technician). The role access matrix (RIA-86) and the API section of the Product Specification gain both.
- **Modules:** `maintenance` stores the assignment; `accounts`, which owns the Keycloak Admin API client, gains a public way to check that a user is a technician.
- **Data:** the `maintenance_ticket` table gains the assigned technician and the time of the assignment (a Flyway migration).
- **Keycloak:** the backend's service account already has `view-users`, which is enough to read a user's realm roles. No realm change.
- **Out of scope:** closing a ticket and its statuses (RIA-94), the operator's list of all tickets and the fleet dashboard (RIA-120), the SLA alert (RIA-95), managing technician accounts (story 1.4), and the frontend screen (RIA-189 already has a stopgap ticket list).
- **Test still to come with RIA-94:** "a closed ticket cannot be reassigned" needs a closed ticket, which does not exist until RIA-94 adds the status. This change specifies the rule; its test is added with RIA-94.
