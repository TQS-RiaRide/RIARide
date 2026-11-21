# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Group project for the TQS course: a *light-mobility fleet-as-a-service* platform, graded on both the product and the evidence that quality was built in from the start. A practice only counts if the QA Manual's compliance table links to evidence of it working.

The backend (Spring Boot 4.1, Java 21, Spring Modulith, Maven) and frontend (React, Vite, TypeScript) walking skeletons exist. `simulator/` is still empty; document its commands here when it lands.

### Backend commands

Run from `backend/`, with JDK 21 active (`java -version`).

| Task | Command |
|---|---|
| Unit tests (Surefire, `*Test.java` / `*Tests.java`) | `./mvnw test` |
| Integration tests only (Failsafe, `*IT.java`) | `./mvnw verify -DskipUnitTests=true` |
| All tests and JaCoCo report | `./mvnw verify` |
| Single unit test | `./mvnw test -Dtest=ModularityTests` (or `-Dtest=ModularityTests#verifiesModuleStructure`) |
| Single integration test | `./mvnw verify -DskipUnitTests=true -Dit.test=HealthEndpointIT` |
| Acceptance tests (Cucumber, Failsafe; needs Docker) | `./mvnw verify -DskipUnitTests=true -Dit.test=CucumberIT` (reports in `target/cucumber-reports/`; `@wip` scenarios are excluded) |
| Run the app | `./mvnw spring-boot:run` (health at `http://localhost:8080/actuator/health`) |
| Docker image | `docker build -t riaride-backend .` |

Payments use Stripe test mode behind the `PaymentGateway` port (ADR 0008). The backend reads the secret key from `STRIPE_SECRET_KEY` and fails at startup if the key is missing or is not a test mode key (`sk_test_...`). To run the app without making payments, any `sk_test_` value works. The tests stub Stripe with WireMock and use a dummy key from `src/test/resources/config/application.yml`, so they need no real key or network access.

To skip unit tests use `-DskipUnitTests=true`, not `-DskipTests`: the pom binds Surefire's `skipTests` to `skipUnitTests`, so `-DskipTests` does not skip them.

### Frontend commands

Run from `frontend/`, with Node.js 22.22.2 or later in the 22 line, or 24.15.0 or later (24 LTS recommended). Details in [`frontend/README.md`](frontend/README.md).

| Task | Command |
|---|---|
| Install dependencies | `npm ci` |
| Dev server (`/api/v1` forwarded to `http://localhost:8080`; `VITE_BACKEND_URL` overrides it) | `npm run dev` (at `http://localhost:5173`) |
| Lint (typescript-eslint strict, react-hooks) | `npm run lint` |
| Format / check formatting (Prettier) | `npm run format` / `npm run format:check` |
| Unit tests (Vitest, React Testing Library) | `npm test` |
| Tests with LCOV coverage (`coverage/`, read by SonarQube) | `npm run test:coverage` |
| Single test file or test | `npx vitest run src/features/health/api.test.ts -t "unreachable"` |
| Type check and production build (`dist/`) | `npm run build` |
| Docker image | `docker build -t riaride-frontend .` |

All repository content is written in English: code, documentation, reports, pull requests and commits.

Product definitions and backlog:

- Idea and business rules: [team Google Doc](https://docs.google.com/document/d/1w1e6eqr99A0CTGNzh3KBUNgs5tphVJQwKrlbpchY1xo/edit) (in Portuguese)
- Backlog: [Jira, project `RIA`](https://riaride.atlassian.net/jira/software/projects/RIA/boards/1)
- QA practices: [QA Manual](docs/reports/qa-manual/qa-manual.md). Its definition of done, contributor guidelines and review checklist are binding; keep this file consistent with it.

## Domain

- **City:** Vila Ria (fictional). All company, institution and city names must be fictional.
- **Institutional client:** Grupo Hoteleiro Marés, a hotel group with the hotels Marés Centro, Marés Praia and Marés Salinas.
- **Operator:** RodaLeve Fleet Services (e-bikes, maintenance, redistribution, batteries).
- **Flow:** at check-in, the hotel registers the stay and the system generates a stay code. The guest creates an account with an email, a password and that code, buys a pass and makes trips between hubs (hotels and tourist spots), picking up and returning at any hub in the network. The group publishes suggested routes that start and end at a hotel.
- **Roles:** guest, operator (including technician), hotel manager, admin.
- **UI languages:** Portuguese and English.

### Business rules (draft)

| ID | Rule | Proposal |
|---|---|---|
| BR1 | Access | Only guests with an active stay can register and book |
| BR2 | Pass types | 1-day pass (24 h after activation), 3-day pass and stay pass (valid until check-out) |
| BR3 | Pricing | One base price, used by all hotels in the group |
| BR4 | Activation | On the first trip, not at purchase |
| BR5 | Deposit | Charged at purchase, refunded at the end of the pass if there are no problems |
| BR6 | One bike at a time | Each pass can only have one vehicle in use |
| BR7 | Maximum trip duration | 3 h; beyond that, overtime is charged per 15 min |
| BR8 | Pass expires during a trip | The trip continues; time after expiry is charged as overtime |
| BR9 | Check-out | The stay pass ends at check-out; if the bike was not returned, the extra time is overtime |
| BR10 | End of trip | Only at a hub with a free slot; capacity is defined by the client |
| BR11 | Booking | Can be made in advance; the bike is held for 15 min |
| BR12 | Battery | A vehicle below 20 % cannot be booked (value comes from telemetry) |
| BR13 | Unlock timeout | A trip whose unlock is not confirmed by telemetry within 30 s fails; the pass stays inactive and the bike stays reserved until the booking expires (BR11) |

Gherkin scenarios tag the rules they cover with these IDs (`@BR10`); hub capacity is part of BR10.

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

- One feature branch per story, created from `dev` and merged into `dev`; merges only through a pull request approved by another member. `main` is the release branch: `dev` is merged into it by a pull request at the end of each iteration, before the tag. Nobody pushes directly to `dev` or `main`.
- The Jira key (for example, `RIA-12`) must appear in the branch name, the commits and the pull request.
- Branch names: `<type>/RIA-<n>-<short-description>`, for example `feat/RIA-31-book-vehicle`.
- Commits: Conventional Commits with the key, for example `feat: RIA-31 add booking endpoint`. Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `perf`.
- Merge with a merge commit, never squash, so test-first (TDD) commits stay in the history of `dev` and `main`. For TDD components, commit the failing test before the implementation.
- Tags at the end of each iteration: `iter1`, `iter2`, ..., `final_v1`. At each tag, a PDF of each report goes in `docs/`.

### Expected tests

| Type        | Rule                                                                                                                                                                  |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| BDD         | Acceptance criteria of core stories in Gherkin, written at grooming and approved by the Product Owner; Cucumber JVM with REST Assured against the API                |
| Unit        | All business logic, with boundary values; JUnit 5, Mockito, AssertJ / Vitest, React Testing Library / pytest. Business rules are developed test first (TDD)            |
| Integration | `*IT.java`, run by Failsafe separately from Surefire unit tests. Testcontainers for PostgreSQL and Mosquitto; WireMock for payments; never an in-memory database         |
| Contract    | MQTT message formats are JSON Schemas in the repo, validated by the simulator's tests and the backend adapter's tests                                                 |
| E2E         | Playwright (TypeScript) with axe accessibility checks, against the Compose stack; nightly on `main`, on demand and before tags, **not** on every build                 |
| Load        | k6 on book-and-start: p95 < 500 ms and errors < 1 % at 50 virtual users; stress and soak before the final tag                                                         |
| Pairwise    | PICT models in the repo feed JUnit parameterized tests where inputs interact                                                                                          |

Tests are linked to stories in Xray (scenario tag `@RIA-n`, JUnit `@Requirement("RIA-n")`), and CI imports results from `main` there.

### Quality and CI

- CI on every push and pull request: build, unit and integration tests, quality gate, with a status badge and author notification on failure.
- SonarQube Cloud with JaCoCo, Vitest LCOV and pytest-cov. Quality gate on new code: coverage ≥ 80 %, duplication ≤ 3 %, A ratings, 100 % hotspots reviewed. It is a required check and blocks the merge.
- Dependency vulnerability scanning on every build: OWASP Dependency-Check (fails on CVSS ≥ 7), `npm audit --audit-level=high`, `pip-audit`. Trivy scans images before they are pushed.
- Never weaken or delete a test, or lower a threshold, to make a build pass.

### Code style

Full guidelines and review checklist: QA Manual, section 3.1 and 3.2.

- **Java:** Google Java Style, enforced by Spotless (google-java-format). Package by Spring Modulith module, not by layer; constructor injection only; business rules in the domain, not in controllers; errors as RFC 9457 problem details.
- **TypeScript/React:** `strict` mode, typescript-eslint strict and react-hooks rules, Prettier. Organize by feature; UI text only through translation files.
- **Python:** ruff for lint and format; type hints on public functions.
- **Tests:** arrange, act, assert; names state the behaviour (`shouldRejectBookingWhenBatteryBelow20Percent`); fixed clocks for time rules, no sleeps; Gherkin scenarios tagged with the story key and business rule (`@RIA-31 @BR1`).

### Definition of done

A story is done when: acceptance criteria met (Gherkin + Cucumber for core stories); unit tests for new business logic; integration tests for what it touches (API, persistence with Testcontainers, simulator or payment adapter); a Playwright E2E test if a core UI flow changes; CI green (build, tests, quality gate, no unresolved HIGH/CRITICAL dependency finding); approved by another member with the AI findings table filled in; tests linked in Xray; affected docs updated (API docs, this file, QA Manual); deployed from `main` (from I3); accepted by the Product Owner.

### AI rules

- **Agents never merge.** A human approves every pull request, and any member may have to explain any merged code.
- Do not give AI services secrets, credentials or personal data. Use synthetic data only.
- AI review on pull requests, recording accepted and rejected findings (see `.github/pull_request_template.md`).
- At least two stories must be agent-generated from Gherkin or an OpenSpec proposal and delivered as pull requests.
- Every pull request has exactly one label: `ai:none`, `ai:assisted` or `ai:generated`. The `AI label` check (`.github/workflows/ai-label.yml`) fails a pull request with none, more than one, or an unknown `ai:*` label. It blocks the merge once it is a required check in branch protection (pending, DevOps Master). Dependabot pull requests get `ai:none` automatically.
- Agent-generated stories follow test author → implementer → reviewer, with a human checkpoint after each step; the implementer must not edit the tests written in the first step.
- Jira (Atlassian MCP): read the story, acceptance criteria and Xray Tests before writing code or tests; you may comment with the PR link and move a story to *In progress* or *In review*. Never move a story to *Done*, create, split or estimate stories, or edit Gherkin in Jira (QA Manual 6.4).
- SonarQube (MCP and `sonar-*` skills): read the quality gate, new issues, coverage gaps and duplications, and fix issues in code. Never mark a hotspot as reviewed, accept or close an issue, or change the quality gate, quality profile or coverage exclusions (QA Manual 6.4).
- These instructions must stay consistent with the QA Manual (style, definition of done, test rules, build commands).

### Reports

Product Specification report (draft after I1) and QA Manual (draft in I2). Sources are Markdown in `docs/reports/` (`product-specification/`, `qa-manual/`); the PDF is produced at each iteration tag.

## Schedule

| Iteration    | Start (P1/P2; P3/P4 +3 days)   | Focus                                                                                                                                  |
|--------------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| I1           | 10 Nov                         | Personas, stories, architecture, walking skeleton with CI, specification draft, SQA tools                                              |
| I2           | 17 Nov                         | First core stories (one generated from a specification), minimal simulator integrated, blocking quality gate, Xray, SLOs, QA Manual v1 |
| I3           | 24 Nov                         | CD, complete API, persistence, integration tests                                                                                       |
| I4           | 1 Dec                          | MVP with representative data, k6, E2E, valued practices                                                                                |
| I5           | 8 Dec                          | Stabilize, reports and critical AI analysis, final tag                                                                                 |
| Presentation | 15 Dec (P1/P2), 18 Dec (P3/P4) | Demo of the deployed MVP and defense of the team's choices                                                                             |
