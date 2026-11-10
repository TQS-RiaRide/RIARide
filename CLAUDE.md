# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Group project for the TQS course: a *light-mobility fleet-as-a-service* platform, graded on both the product and the evidence that quality was built in from the start. A practice only counts if the QA Manual's compliance table links to evidence of it working.

**There is no code yet.** `backend/`, `frontend/` and `simulator/` are empty, so no build, lint or test commands exist. When the walking skeleton (I1) lands, document the commands here, including how to run a single test, and update the "Proposed decisions" section.

All repository content is written in English: code, documentation, reports, pull requests and commits.

Product definitions and backlog:

- Idea and business rules: [team Google Doc](https://docs.google.com/document/d/1w1e6eqr99A0CTGNzh3KBUNgs5tphVJQwKrlbpchY1xo/edit) (in Portuguese)
- Backlog: [Jira, project `SCRUM`](https://riaride.atlassian.net/jira/software/projects/SCRUM/boards/1)

## Domain

- **City:** Vila Ria (fictional). All company, institution and city names must be fictional.
- **Institutional client:** Grupo Hoteleiro Marés, a hotel group with the hotels Marés Centro, Marés Praia and Marés Salinas.
- **Operator:** RodaLeve Fleet Services (e-bikes, maintenance, redistribution, batteries).
- **Flow:** at check-in, the hotel registers the stay and the system generates a stay code. The guest creates an account with an email, a password and that code, buys a pass and makes trips between hubs (hotels and tourist spots), picking up and returning at any hub in the network. The group publishes suggested routes that start and end at a hotel.
- **Roles:** guest, operator (including technician), hotel manager, admin.
- **UI languages:** Portuguese and English.

### Business rules (draft)

| Rule | Proposal |
|---|---|
| Access | Only guests with an active stay can register and book |
| Pass types | 1-day pass (24 h after activation), 3-day pass and stay pass (valid until check-out) |
| Pricing | One base price, used by all hotels in the group |
| Activation | On the first trip, not at purchase |
| Deposit | Charged at purchase, refunded at the end of the pass if there are no problems |
| One bike at a time | Each pass can only have one vehicle in use |
| Maximum trip duration | 3 h; beyond that, overtime is charged per 15 min |
| Pass expires during a trip | The trip continues; time after expiry is charged as overtime |
| Check-out | The stay pass ends at check-out; if the bike was not returned, the extra time is overtime |
| End of trip | Only at a hub with a free slot; capacity is defined by the client |
| Booking | Can be made in advance; the bike is held for 15 min |
| Battery | A vehicle below 20 % cannot be booked (value comes from telemetry) |

Payments are always made by the guest. Invoicing the hotel is out of scope.

Open business questions are listed in section 13 of the [architecture proposal](docs/architecture/README.md).

### Required MVP

- **Guest:** find vehicles by hub and type, book or start immediately, end at a hub; dashboard with the pass, active and past trips, and charges.
- **Operator:** register vehicles and hubs; vehicle lifecycle (`available`, `reserved`, `in use`, `maintenance`, `retired`), including activating and deactivating; maintenance tickets with an SLA deadline assigned to technicians; fleet and ticket dashboard.
- **Hotel backoffice:** rules of use, hub capacity, business KPIs (utilization, availability against the SLA, trips per day, time to repair) and technical metrics (API latency, error rate, telemetry lag).
- **Payments:** deposit and overtime in a sandbox (for example, Stripe test mode) or a simulated gateway.
- **Out of scope:** B2B invoicing, real hardware, native apps, hub telemetry, real payments.

## Architecture: constraints from the assignment

The architecture proposal is I1 work. Already fixed:

- **Backend:** Spring Boot (stable) and Java 21. The machine's default JDK may differ, so confirm 21 is in use.
- **Frontend:** a responsive web UI is required.
- **Simulator:** a separate service, in its own container, emitting battery, position or hub, lock state and fault events per vehicle. The platform reads it only through **one** integration interface (for example, MQTT), behind an adapter. Switching simulators must only require replacing the adapter, with no change to business logic.
- **Payments:** also behind an adapter, covered by integration tests.
- **Infrastructure as code:** production environment described in, for example, Docker Compose. `main` deploys automatically, with representative data, to an environment reachable by the teaching staff.

### Proposed decisions

Pending team review. Full rationale, diagrams and ADRs are in [`docs/architecture/`](docs/architecture/README.md).

- **Backend:** modular monolith with Spring Modulith, built with Maven. Ports and adapters only at external integrations.
- **Database:** PostgreSQL only, with Flyway.
- **Frontend:** React, Vite and TypeScript.
- **Simulator:** Python, bidirectional MQTT through Mosquitto.
- **Payments:** Stripe test mode.
- **Authentication:** Keycloak OIDC; guest registration goes through the backend and the Keycloak Admin API.
- **Deployment:** Docker Compose on a university VM, with images in GHCR. VM availability is still to be confirmed.

## Working rules

### Git and Jira

- One feature branch per story; protected `main`; merges only through a pull request approved by another member.
- The Jira key (for example, `SCRUM-12`) must appear in the branch name, the commits and the pull request.
- Tags at the end of each iteration: `iter1`, `iter2`, ..., `final_v1`. At each tag, a PDF of each report goes in `docs/`.

### Expected tests

| Type        | Rule                                                                                                 |
|-------------|------------------------------------------------------------------------------------------------------|
| BDD         | Acceptance criteria of core stories in Gherkin, automated with Cucumber                              |
| Unit        | Cover business logic                                                                                 |
| Integration | API, persistence with Testcontainers, simulator and payment adapters; run separately from unit tests |
| E2E         | Selenium or Playwright for core use cases; must **not** run on every build                           |
| Load        | k6 with thresholds on a critical flow, against SLOs defined in the Product Specification report      |

Tests are linked to stories in Xray, and CI results are imported there.

### Quality and CI

- CI on every push and pull request: build, unit and integration tests, quality gate, with a status badge and author notification on failure.
- SonarQube and JaCoCo, with a quality gate on new code that blocks the merge.
- Dependency vulnerability scanning on every build.

### AI rules

- **Agents never merge.** A human approves every pull request, and any member may have to explain any merged code.
- Do not give AI services secrets, credentials or personal data. Use synthetic data only.
- AI review on pull requests, recording accepted and rejected findings (see `.github/pull_request_template.md`).
- At least two stories must be agent-generated from Gherkin or an OpenSpec proposal and delivered as pull requests.
- These instructions must stay consistent with the QA Manual (style, definition of done, test rules, build commands).

### Reports

Product Specification report (draft after I1) and QA Manual (draft in I2).

## Schedule

| Iteration    | Start (P1/P2; P3/P4 +3 days)   | Focus                                                                                                                                  |
|--------------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| I1           | 10 Nov                         | Personas, stories, architecture, walking skeleton with CI, specification draft, SQA tools                                              |
| I2           | 17 Nov                         | First core stories (one generated from a specification), minimal simulator integrated, blocking quality gate, Xray, SLOs, QA Manual v1 |
| I3           | 24 Nov                         | CD, complete API, persistence, integration tests                                                                                       |
| I4           | 1 Dec                          | MVP with representative data, k6, E2E, valued practices                                                                                |
| I5           | 8 Dec                          | Stabilize, reports and critical AI analysis, final tag                                                                                 |
| Presentation | 15 Dec (P1/P2), 18 Dec (P3/P4) | Demo of the deployed MVP and defense of the team's choices                                                                             |
