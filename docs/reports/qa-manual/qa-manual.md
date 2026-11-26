# RiaRide: quality manual

Maria Mané (125102), Inês Lourenço (125895), Martim Gil (124833), Tomás Xavier (125438)  
v2026-11-24

> Version 1 (iteration I2, RIA-70). This manual tells a new team member which QA practices the team follows and why, and links each one to its evidence. The practices are decided from the start; the evidence links are added as each practice starts working.

## 1. Practices compliance analysis

Status values:

- **Done:** in place, with a link to evidence of it working.
- **Partial:** partly in place, with evidence of what exists.
- **Decided (I*n*):** the policy is defined in this manual; the evidence is due in iteration I*n*.
- **Not adopted:** a valued practice the team chose not to adopt.

A practice counts only when its evidence link shows it working. Expected (E) practices come first. Valued (V) practices are planned in priority order: AI6, needed by the first generated story (AI4), and Q6, T6, T8 and D6, as they reuse tools the expected practices already need, first; T7, T10 and AI7 next; T9 and D5 only if time allows. A V practice that is not working by I5 is marked *Not adopted*, with the reason.

| ID | Practice | Level | Status | Evidence / notes |
|---|---|---|---|---|
| PM1 | Scrum-like management in Jira | E | Partial | [Board](https://riaride.atlassian.net/jira/software/projects/RIA/boards/1) with product backlog and sprints I1, I2; section 2.2 |
| PM2 | User stories with points and acceptance criteria | E | Partial | Section 2.2 |
| PM3 | Jira linked to Git | E | Partial | Keys in branches, commits and PRs: [PR #1](https://github.com/TQS-RiaRide/RIARide/pull/1), [PR #2](https://github.com/TQS-RiaRide/RIARide/pull/2). The development panel needs the GitHub for Jira app. Early commits pushed straight to `main` before protection carry no key |
| PM4 | Burndown and velocity | E | Decided (I2) | Section 2.2; charts available once sprint I1 closes |
| PM5 | Requirements coverage in Xray | E | Partial | Section 2.3. [Xray import workflow](https://github.com/TQS-RiaRide/RIARide/blob/main/.github/workflows/xray-import.yml) runs after CI on `main`; its [first run](https://github.com/TQS-RiaRide/RIARide/actions/runs/44077318930) imported the JUnit reports as Test Execution [RIA-169](https://riaride.atlassian.net/browse/RIA-169). Test Plan [RIA-146](https://riaride.atlassian.net/browse/RIA-146) holds one Test per I2 story (RIA-147 to RIA-156, linked to RIA-50 to RIA-59), for each story's main scenario (section 2.3); the Cucumber scenarios run in CI since RIA-68. The [import of the I2 release](https://github.com/TQS-RiaRide/RIARide/actions/runs/45394068765) failed: Xray rejected the JUnit report because some tests named a task (RIA-62) as their requirement, and the simulator and Cucumber imports were skipped. That import stays failed: its reports name RIA-62, so importing them again would fail too. RIA-105 links tests to stories only, keeps the imports independent, adds the frontend results and a manual re-import; it applies from the I3 release, whose Traceability report will close this row |
| T1 | BDD: Gherkin scenarios automated with Cucumber | E | Done | Section 5.3. [Feature files](https://github.com/TQS-RiaRide/RIARide/tree/dev/backend/src/test/resources/features) for the I2 stories, run by `CucumberIT` in the integration tests of every CI run since [PR #22](https://github.com/TQS-RiaRide/RIARide/pull/22); stories still in progress keep their scenarios tagged `@wip` |
| T2 | Unit tests for business logic | E | Done | Section 5.2. The backend unit tests (Surefire), Vitest and pytest run in the [CI pipeline](https://github.com/TQS-RiaRide/RIARide/actions/workflows/ci.yml) on every push; JaCoCo, Vitest and pytest-cov coverage is imported into SonarQube |
| T3 | Integration tests, run separately | E | Done | Section 5.2. `*IT` classes run by Failsafe in their own CI step, with PostgreSQL, Keycloak and Mosquitto in Testcontainers and Stripe stubbed by WireMock; the simulator runs its broker tests separately with `pytest -m integration` ([PR #37](https://github.com/TQS-RiaRide/RIARide/pull/37)) |
| T4 | End-to-end tests of the web UI | E | Decided (I4) | Section 5.3 |
| T5 | SLOs checked by a k6 load test | E | Decided (I4) | Section 5.4; SLOs in Product Specification section 4.2 |
| T6 | Test-driven development | V | Done | Section 5.1. Failing tests committed before the implementation and kept visible by merge commits, for example [PR #36](https://github.com/TQS-RiaRide/RIARide/pull/36) (RIA-52), [PR #48](https://github.com/TQS-RiaRide/RIARide/pull/48) (RIA-57) and [PR #37](https://github.com/TQS-RiaRide/RIARide/pull/37) (RIA-60) |
| T7 | Pairwise testing | V | Decided (I4) | Section 5.5 |
| T8 | Automated accessibility checks | V | Decided (I4) | Sections 3.4 and 5.4 |
| T9 | GBFS feed contract test | V | Decided (I4) | Section 5.5; depends on the Product Owner adopting the GBFS extension |
| T10 | Further non-functional tests | V | Decided (I5) | Section 5.4 |
| Q1 | Static analysis and coverage dashboard | E | Done | Section 3.3. [SonarQube Cloud dashboard](https://sonarcloud.io/project/overview?id=TQS-RiaRide_RIARide), analysed by the SonarQube job of [`ci.yml`](../../../.github/workflows/ci.yml) on every push and pull request. Agents read its results (section 6.4) |
| Q2 | Blocking quality gate on new code | E | Done | Section 3.3. The gate result is part of the required `CI result` check (RIA-160, [PR #51](https://github.com/TQS-RiaRide/RIARide/pull/51)); [PR #37](https://github.com/TQS-RiaRide/RIARide/pull/37) was blocked by a security rating of C until its dependencies were installed as hashed wheels |
| Q3 | Dependency vulnerability scanning | E | Partial | [`dependabot.yml`](https://github.com/TQS-RiaRide/RIARide/blob/main/.github/dependabot.yml) on `main` for Maven, npm, pip and GitHub Actions. Updates for a component fail until its manifest exists. Per-build scan: section 3.4 |
| Q4 | Peer code review | E | Done | [PR template](../../../.github/pull_request_template.md), section 3.2; branch protection on `dev` and `main` requires one approval (section 4.1) |
| Q5 | Contributor guidelines | E | Done | Section 3.1 |
| Q6 | Container image scanning or added security testing | V | Decided (I4) | Section 3.4 |
| D1 | Feature branches, protected main, merge by pull request | E | Partial | Branch per story in use, merged into `dev` by pull request; `main` and `dev` protected; branch name and Jira key enforced by [`pr-rules.yml`](../../../.github/workflows/pr-rules.yml) (section 4.1). *Link to a blocked pull request to be added.* |
| D2 | CI with quality gate, badge and failure notification | E | Done | [`ci.yml`](../../../.github/workflows/ci.yml) runs the backend, frontend, simulator and SonarQube jobs and a summary job on every push and pull request; the CI badge is in the [README](../../../README.md); GitHub emails the author when a run fails. Section 4.2 |
| D3 | Infrastructure as code | E | Decided (I3) | Section 4.2 |
| D4 | Continuous delivery with representative data | E | Decided (I3) | Section 4.2 |
| D5 | Separate staging and production | V | Decided (I4) | Section 4.2 |
| D6 | Observability with SLO alerts | V | Decided (I4) | Section 4.3 |
| AI1 | Team AI policy and declaration of AI use | E | Done | Sections 6.1 and 6.2 |
| AI2 | Versioned agent instructions | E | Done | [`AGENTS.md`](../../../AGENTS.md) → [`CLAUDE.md`](../../../CLAUDE.md), covering style, definition of done, test rules, backend and frontend build commands and the limits on agents' Jira and SonarQube access. Sections 6.3 and 6.4 |
| AI3 | AI-assisted code review | E | Done | [`ai-review.yml`](../../../.github/workflows/ai-review.yml) and [`docs/ai-review.md`](../../ai-review.md). Evidence: [#25](https://github.com/TQS-RiaRide/RIARide/pull/25), where the Claude review's finding is recorded as partly accepted in the AI review findings table, and [#40](https://github.com/TQS-RiaRide/RIARide/pull/40). Findings are counted by severity and decision with [`tools/ai-review-findings`](../../../tools/ai-review-findings/README.md) (RIA-108). Section 6.6 |
| AI4 | Specification-driven generation (≥ 2 stories) | E | Partial | Section 6.5. Story 1, RIA-56 Register a hub ([PR #35](https://github.com/TQS-RiaRide/RIARide/pull/35)), generated from its approved Gherkin scenarios through the agent flow of section 6.3; story 2 is planned for I3 from an OpenSpec proposal |
| AI5 | Critical analysis of AI's contribution | E | Decided (I5) | Section 6.7; data collected from every PR from now on |
| AI6 | Coordination of several agents | V | Done | Section 6.3: the agents are defined in [`.claude/agents/`](../../../.claude/agents/), first used for RIA-56 ([PR #35](https://github.com/TQS-RiaRide/RIARide/pull/35)), and the `Agent boundaries` CI check enforces their roles |
| AI7 | Agents in the CI pipeline | V | Decided (I3) | Section 6.6: PR summary and failure triage. The AI review job counts as AI3, not here |

## 2. Project management

### 2.1 Assigned roles

| Role | Team member |
|---|---|
| Team Coordinator | Maria Mané |
| Product Owner | Inês Lourenço |
| QA Engineer | Martim Gil |
| DevOps Master | Tomás Xavier |

Every member also develops stories.

### 2.2 Backlog grooming and progress monitoring

- **Backlog:** Jira project `RIA`, one epic per area, user stories as work items. The Product Owner keeps the backlog ordered by business value.
- **Iterations:** one-week sprints, I1 to I5, starting on the day of the in-class checkpoint.
- **Grooming (backlog refinement):** 30 minutes right after each checkpoint, before planning. The Product Owner and the team clarify the top stories, write their acceptance criteria (Gherkin scenarios for core stories, approved by the Product Owner), split stories that do not fit an iteration, and estimate them.
- **Estimation:** story points on the Fibonacci scale (1, 2, 3, 5, 8, 13), by planning poker. A story above 8 is split.
- **Planning:** the team pulls groomed stories into the sprint up to its recent velocity. Only stories with points and acceptance criteria enter a sprint.
- **Acceptance:** the Product Owner moves a story to *Done* when it meets the definition of done (section 4.1).
- **Jira and Git:** the story key appears in the branch name, every commit and the pull request title (section 4.1). The GitHub for Jira app shows branches, commits and PRs in each story's development panel.
- **Jira and agents:** coding agents read the story and its acceptance criteria from Jira and comment on it, within the limits of section 6.4.
- **Progress:** the burndown chart is reviewed mid-week by the Team Coordinator, and burndown and velocity are shown at each checkpoint.

### 2.3 Requirements coverage tracking

- **Source of truth:** Gherkin scenarios live in the repository, never edited in Jira.
- **Linking:** each scenario is tagged with its story key (`@RIA-31`), so Xray links the Test it creates to that story. Xray creates one Test per scenario, so a Test key belongs to a single scenario. Each story planned for an iteration has one Test created in Jira beforehand (for I2, RIA-147 to RIA-156; for I3, RIA-152 for RIA-55, RIA-177 for RIA-92 and RIA-178 to RIA-188 for the other I3 stories): only the story's main scenario, the first in its feature file, carries that key (`@RIA-50 @TEST_RIA-147`), so the import updates that Test instead of creating a duplicate. The story's other scenarios carry only the story key, and the first import creates their Tests, linked to the story. JUnit tests that verify a story declare it with `@Requirement("RIA-31")` from the Xray JUnit extensions, on each test method (the extensions ignore it on the class). CI sends every JUnit result to Xray through the extensions' report, so tests without a story are imported too, unlinked. Frontend tests declare their story with `requirement('RIA-31')` from `frontend/src/test/xray.ts`, once per test: at the top of the file or of one `describe` block. The simulator's tests declare it per test module in `simulator/tests/conftest.py`; only the contract and broker tests verify a story (RIA-58). Xray only accepts stories as requirements: a key of a task, epic or subtask makes it reject the whole report, so a test that verifies a task (an adapter, a setup) is linked to the story it serves, or to none.
- **Import:** after every CI run on `main`, the `Xray import` workflow sends the JUnit XML and Cucumber JSON reports to the Xray Cloud API. Xray imports each format through its own endpoint, so a run creates up to four Test Executions: one for the backend JUnit reports, one for the frontend's Vitest JUnit report, one for the simulator's pytest JUnit reports and one for Cucumber. Each import runs even if an earlier one failed, so one rejected report does not hide the others, and the job still fails. To import a run again after a failure, start the workflow by hand with the id of a completed CI run of a push to `main`; the results go to the Test Plan in `XRAY_TEST_PLAN_KEY` at that moment, not to the plan of the run's iteration. Re-importing only helps when the reports themselves were valid: a run whose tests name a task as their requirement fails again. The automatic import always runs the workflow as it is on `main`, so a change to it applies from the next release; GitHub offers the manual run only once the workflow is on `main`, and then from any branch, so it is started from `main`. The pytest tests carry their story in a `requirements` property, recorded by `simulator/tests/conftest.py`. The workflow reads the organization secrets `XRAY_CLIENT_ID` and `XRAY_CLIENT_SECRET` and the organization variable `XRAY_TEST_PLAN_KEY`, and fails with a clear message if any of them is empty.
- **Plans:** one Test Plan per iteration groups the Test Executions of that iteration. At the start of each iteration, the QA Engineer creates the new Test Plan and updates `XRAY_TEST_PLAN_KEY` to its key; until then, executions go to the previous iteration's plan.
- **Coverage:** the Xray requirement coverage report is checked at each checkpoint. A story without linked tests is not *Done*; any exception is explained in the story. After the import of each iteration's release, the QA Engineer exports the Traceability report of that iteration's stories, filtered by its Test Plan, to PDF in `docs/reports/qa-manual/evidence/` and links it from the PM5 row of the compliance table.

## 3. Code quality management

### 3.1 Guidelines for contributors

These guidelines are short on purpose: they state the key choices, and the referenced style guides cover the rest. Formatting is checked by tools in CI, so reviews do not discuss it.

**Language and data**

- All code, comments, commits and documentation are in English. The UI is translated into Portuguese and English.
- No secrets in the repository: they live in GitHub Secrets or local `.env` files that are not committed. Test and seed data are synthetic, with fictional names only.

**Backend (Java 21, Spring Boot)**

- Style: [Google Java Style](https://google.github.io/styleguide/javaguide.html), applied by [Spotless](https://github.com/diffplug/spotless) with google-java-format. `./mvnw spotless:apply` formats the code; `spotless:check` fails the build when the code is not formatted.
- Package by domain module (Spring Modulith), not by layer. A module exposes only its API package; other modules must not use its internals. Ports and adapters are used only at external integrations (MQTT, Stripe, Keycloak).
- Constructor injection only; no field injection.
- Domain rules live in the domain classes and services, not in controllers. Controllers validate input (Bean Validation) and map to DTOs.
- Errors reach clients as RFC 9457 problem details, with no stack traces or internal data.
- Time comes from an injected `java.time.Clock`, so time rules can be tested.

**Frontend (React, TypeScript)**

- TypeScript `strict` mode; no `any` without a comment explaining why.
- [typescript-eslint](https://typescript-eslint.io/) strict rules and `eslint-plugin-react-hooks`; formatting by [Prettier](https://prettier.io/).
- Organize by feature, not by file type. UI text goes through the translation files, never hard-coded.

**Simulator (Python)**

- [PEP 8](https://peps.python.org/pep-0008/) through [ruff](https://docs.astral.sh/ruff/) for linting and formatting; type hints on public functions.

**Tests**

- Arrange, act, assert, with names that state the behaviour: `shouldRejectBookingWhenBatteryBelow20Percent`.
- One behaviour per test; no logic or sleeps in tests. Use deterministic waits and fixed clocks for time rules.
- Gherkin scenarios are tagged with the story key and the business rule they cover, for example `@RIA-31 @BR1`. The rule IDs (BR1 to BR13) are in the business rules table of [`CLAUDE.md`](../../../CLAUDE.md).

### 3.2 Code review practice

- **When:** every change to `dev` or `main` goes through a pull request. No direct pushes.
- **Who approves:** at least one team member other than the author. The author merges after approval and a green CI. Agents never approve or merge.
- **AI review:** each non-draft pull request receives an advisory review from Claude ([`docs/ai-review.md`](../../ai-review.md)), with findings tagged `CRITICAL`, `HIGH`, `MEDIUM` or `LOW`. The author records each finding in the PR template's *AI review findings* table: the finding starts with its severity (`HIGH: ...`), then the decision (Accepted, Accepted in part, Rejected or Deferred) and the reason. The human reviewer checks that table before approving. [`tools/ai-review-findings`](../../../tools/ai-review-findings/README.md) counts the findings of every pull request by severity and decision (section 6.7).

**Review checklist**

- [ ] The pull request names the story key, and the change matches the story's acceptance criteria.
- [ ] The tests required by the definition of done exist and test behaviour, not implementation details.
- [ ] Business rules sit in the domain, not in controllers or the UI.
- [ ] Module boundaries are respected; external systems are reached only through their adapter.
- [ ] Input is validated at the boundary; errors are handled and do not leak internal data.
- [ ] No secrets, credentials or personal data; test data is synthetic.
- [ ] Security hotspots raised by SonarQube on this change are reviewed.
- [ ] The AI review findings table is filled in, each finding starts with its severity, and the reasons for rejections are sound.
- [ ] The pull request has its AI label (section 6.2); the `AI label` check fails without it.
- [ ] Documentation that the change affects is updated (API documentation, `CLAUDE.md`, this manual).
- [ ] The reviewer can explain the change; anyone may be asked to explain any merged code.

### 3.3 Static analysis and quality gates

SonarQube Cloud, on the free plan for open-source projects, analyzes every pull request and every push to `main` from GitHub Actions ([ADR 0014](../../architecture/adr/0014-sqa-tools.md)). It imports coverage from JaCoCo (backend), Vitest LCOV (frontend) and pytest-cov (simulator).

**Quality gate on new code**

| Condition | Threshold | Rationale |
|---|---|---|
| Coverage | ≥ 80 % | SonarQube's default. Business rules are written test first, so they reach it naturally; the 20 % margin absorbs glue code that is better tested by integration tests than by mocks |
| Duplicated lines | ≤ 3 % | SonarQube's default; duplication in new code is cheap to avoid |
| Reliability rating | A | No new bugs |
| Security rating | A | No new vulnerabilities |
| Maintainability rating | A | Technical debt stays below 5 % of the new code's development cost |
| Security hotspots reviewed | 100 % | Each hotspot is a human decision, not left open |

The gate applies to new code only, so old code improves as it is changed and the gate never blocks on history. Overall metrics are followed on the dashboard but do not block. Agents read the gate status and the issues of a pull request to propose fixes, but only people review hotspots or change the gate (section 6.4).

**Coverage exclusions:** only by path, listed in the build configuration: the `config` packages, the `dto` packages, the application `main` class, generated code and test code. A class with logic does not belong in those packages. Adding an exclusion is a change to this manual, reviewed like any other.

Coverage says which lines ran, not whether a test would catch a bug, and AI-generated tests can reach 80 % with weak assertions. Reviewers therefore check that tests assert behaviour (review checklist, section 3.2).

**Blocking:** the SonarQube job is part of the `CI result` check, which is required in the branch protection of `main` and `dev`, so a failing gate blocks the merge. Evidence: [PR #37](https://github.com/TQS-RiaRide/RIARide/pull/37) failed the gate (security rating C, from installing the simulator dependencies without hashes) and could not be merged until the fix; the results are on the [SonarQube Cloud dashboard](https://sonarcloud.io/project/overview?id=TQS-RiaRide_RIARide).

### 3.4 Other rules in code inspection

**Security**

| Check | Tool | When | Fails on |
|---|---|---|---|
| Code issues and hotspots | SonarQube | Every PR and push | Quality gate (section 3.3) |
| Backend dependencies | OWASP Dependency-Check | Every build | CVSS ≥ 7 |
| Frontend dependencies | `npm audit --audit-level=high` | Every build | High or critical |
| Simulator dependencies | `pip-audit` | Every build | Any known vulnerability (pip-audit has no severity filter; the simulator has few dependencies, so this stays manageable) |
| Dependency updates | Dependabot (Maven, npm, pip, GitHub Actions) | Weekly | Opens PRs |
| Container images (Q6) | Trivy | Before each image push | HIGH or CRITICAL with a fix available |
| Secrets | GitHub secret scanning with push protection, enabled by the DevOps Master (free for public repositories) | Every push | Blocks pushes with known secret formats |

The thresholds for Dependency-Check and `npm audit` (CVSS ≥ 7, high) block what is likely exploitable without stopping every build for low-risk findings, which Dependabot still raises as update pull requests.

The author of a pull request fixes or justifies each finding before merging; a justified exception (for example, a vulnerability with no fix, in code the project does not call) is suppressed in the tool's configuration with a comment and recorded in the pull request. The QA Engineer reviews open findings and suppressions before each iteration tag.

**Accessibility**

The web UI targets WCAG 2.1 level AA. Playwright runs axe (`@axe-core/playwright`) on the core pages, and a page fails with any `serious` or `critical` violation (section 5.4). Automated checks find only part of the WCAG failures, so the exploratory sessions (section 5.6) include keyboard-only use of the guest flow.

**Data protection (GDPR)**

The platform stores guests' email, password, stay and trip history, which is personal data even in a demo.

- Collect only what the flows need: no name, address or document number for guests.
- Passwords are held by Keycloak only; card data stays in Stripe and never reaches the backend.
- A trip records its start and end hubs and times. The latest vehicle state is kept on `vehicle`, and every telemetry message, with its position, is kept in `telemetry_event`; joined with `trip`, that history rebuilds a guest's route. A scheduled job therefore deletes `telemetry_event` rows older than 7 days, which is enough for the telemetry lag metrics and for debugging.
- Logs and error responses contain no email, password, token or stay code.
- Seed, test and demo data are synthetic (section 3.1); real data is never loaded into any environment.

## 4. Continuous delivery pipeline (CI/CD) and observability

### 4.1 Development workflow

**Coding workflow**

The team works with two long-lived branches. `dev` is the integration branch: each story is developed in its own short-lived branch and merged into `dev`. `main` is the release branch: it is always deployable, and it receives `dev` through a pull request at the end of each iteration, before the iteration tag. From I3, every merge to `main` deploys to staging (section 4.2).

1. Take a story from the sprint in Jira and move it to *In progress*.
2. Create a branch from `dev` named `<type>/RIA-<n>-<short-description>`, for example `feat/RIA-31-book-vehicle`. Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `perf`. The `PR rules` check ([`pr-rules.yml`](../../../.github/workflows/pr-rules.yml)) fails a pull request to `dev` or `main` whose branch does not follow this pattern or whose title has no Jira key. Dependabot pull requests and the release pull request from `dev` to `main` are exempt.
3. Commit with [Conventional Commits](https://www.conventionalcommits.org/) and the story key: `feat: RIA-31 add booking endpoint`. For TDD components, commit the failing test before the implementation.
4. Open a pull request to `dev` with the template and the AI label. Use a draft while the work is incomplete; the AI review runs when it is marked ready.
5. Address the CI results, the AI review and the human review.
6. Merge with a **merge commit**, not squash, so that test-first commits stay visible in the history of `dev` and `main`. Delete the branch.
7. At the end of the iteration, the DevOps Master opens a pull request from `dev` to `main`. It follows the same review and checks, and is also merged with a merge commit; the iteration tag is then set on `main`.

**Branch protection on `main` and `dev`** (set by the DevOps Master, same rules on both):

- pull request required, with at least one approval;
- stale approvals dismissed when new commits are pushed;
- required status checks: `CI result` (the CI summary job, section 4.2), which also fails when the SonarQube quality gate fails (Q2), and `Exactly one AI label` (sections 3.2 and 6.2). The `PR rules` check runs on every pull request but is not required yet;
- branch up to date before merging;
- all review conversations resolved before merging;
- no force pushes, no deletions, no bypass for administrators.

The repository allows only merge commits; squash and rebase merging are disabled.

Nobody pushes directly to `dev` or `main`.

**AI in the workflow:** see section 6.1.

**Definition of done**

A story is done when all of the following hold:

1. Its acceptance criteria are met. For core stories, they are Gherkin scenarios automated with Cucumber.
2. New business logic is covered by unit tests.
3. What the story touches is covered by integration tests: REST API, persistence (Testcontainers), the simulator adapter or the payment adapter.
4. If the story changes a core UI flow, a Playwright end-to-end test covers it. These tests run outside the regular build, so the author runs the end-to-end workflow on demand on the branch and links the green run in the pull request; the reviewer checks the link.
5. CI passes: build, unit and integration tests, the SonarQube quality gate, and the dependency scan with no unresolved HIGH or CRITICAL finding.
6. Another member approved the pull request, and the AI review findings table is filled in.
7. The story's tests are linked to it in Xray.
8. Affected documentation is updated: API documentation, `CLAUDE.md` when commands or rules change, and this manual when a practice changes.
9. From I3, the change is deployed from `main`.
10. The Product Owner accepted the story in Jira.

### 4.2 CI/CD pipeline and tools

**Continuous integration** (GitHub Actions, on every push and pull request)

| Job | Steps |
|---|---|
| Backend | Spotless check → unit tests (Surefire) → integration and Cucumber tests (Failsafe, Testcontainers) → JaCoCo → OWASP Dependency-Check → SonarQube |
| Frontend | ESLint and Prettier check → Vitest with coverage → build → `npm audit` → SonarQube |
| Simulator | ruff → pytest with coverage → `pip-audit` → SonarQube |
| Tools | ruff → pytest with coverage → SonarQube, for the AI review findings script (section 6.7); it has no runtime dependencies to audit |
| AI review | Advisory review of non-draft pull requests from this repository, when opened, reopened or marked ready (section 6.6) |

The workflow always starts, and every component job runs in parallel on every push and pull request; none is conditional, so a missing or moved project file fails its job instead of skipping it. A final summary job depends on all of them, runs with `if: always()` and fails unless every `needs.<job>.result` is `success`; a `failure`, `cancelled` or `skipped` result fails it. Without `if: always()`, the summary job would itself be skipped when a component job fails, and branch protection treats a skipped required check as passing. It is the required status check. Path filters at workflow level are not used, because a workflow that never starts would leave a required check waiting forever. On `main`, the test reports are also imported into Xray (section 2.3).

- **Failure notification:** GitHub Actions emails the author of the push or pull request when a run fails.
- **Badge:** the CI status badge for `main` is shown in the README.

**Continuous delivery**

- **Infrastructure as code:** Docker Compose files in the repository describe each environment: reverse proxy with the frontend build, backend, PostgreSQL, Keycloak, Mosquitto, simulator, Prometheus and Grafana. Environment differences live in `.env` files on the VM, never in the repository.
- **Images:** a merge to `main` builds the images, scans them with Trivy and pushes them to the GitHub Container Registry.
- **Staging:** every merge to `main` deploys automatically to staging.
- **Production:** an iteration tag (`iter1`, ..., `final_v1`) promotes the images already running in staging to production, after a manual approval in a GitHub Environment. The same image goes to both environments; nothing is rebuilt.
- **Runner:** deployment runs on a self-hosted GitHub Actions runner on the university VM, so the VM does not need to accept connections from GitHub. The repository is public, and a self-hosted runner would run code from any pull request that reaches it, so it is restricted. A runner label alone is not enough, because a workflow edited on any branch could target it with `runs-on: [self-hosted, deploy]` and no `environment:`. Therefore the runner is in a dedicated runner group limited to this repository and to selected workflows (the deploy workflow on `refs/heads/main` and on tags); the deploy jobs also run through a protected GitHub Environment; the repository requires approval for workflow runs from all outside collaborators; and the runner runs as an unprivileged user with no access beyond the Compose stacks. If the organization's plan does not allow restricting a runner group to selected workflows, this section will record it, with the residual risk that a team member's branch can reach the runner.
- **Capacity:** staging and production are two Compose stacks on the same VM. Before D5, the DevOps Master checks that the VM can run both (each with PostgreSQL, Keycloak and the monitoring stack); if not, monitoring is shared and staging stops between uses.
- **Representative data:** the `demo` profile loads synthetic seed data through Flyway: the three Marés hotels, about eight hubs, about forty e-bikes in every lifecycle state, guests with active stays, passes and past trips. The simulator emits telemetry for the seeded vehicles.

*Links to the pipeline definitions and recent runs will be added as each part runs.*

### 4.3 System observability

- **Collection:** Spring Boot Actuator and Micrometer expose metrics in Prometheus format: API latency and error rate per endpoint, JVM and database pool health, and custom metrics for telemetry lag (time from the simulator's event timestamp to its processing), active trips and bookings.
- **Dashboards:** Grafana, with dashboards versioned in the repository and provisioned on start. The hotel backoffice reads the technical metrics (API latency, error rate, telemetry lag) from the same source.
- **Alerts:** Grafana alert rules on the SLOs, sent by email to the team through an SMTP account configured in the VM's `.env`.

The SLOs, with their targets and the tests that check them, are defined in section 4.2 of the Product Specification report. Each has a Grafana alert rule:

| SLO | Alert when |
|---|---|
| SLO1, book-and-start latency | p95 above target for 5 minutes |
| SLO2, API error rate | above target for 5 minutes |
| SLO3, telemetry lag | p95 above target for 5 minutes |
| SLO4, availability | health check failing for 2 minutes |

### 4.4 Artifacts repository

Container images are stored in the GitHub Container Registry, tagged with the commit SHA and, on promotion, with the iteration tag. Maven artifacts are not published: the deployable unit is the image.

## 5. Continuous testing

### 5.1 Overall testing strategy and test development practice

Most tests are fast developer tests; fewer, slower tests cover acceptance, the UI and performance. Every kind of test has a fixed place in the pipeline:

| Test kind | Tools | Runs |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ; Vitest with React Testing Library; pytest | Every push |
| Integration (API, persistence, adapters) | Spring Boot Test, Testcontainers, REST Assured, WireMock | Every push, after unit tests |
| Acceptance (BDD) | Cucumber JVM with REST Assured | Every push, with integration tests |
| Contract (simulator messages) | JSON Schema, checked in Java and Python | Every push |
| End-to-end and accessibility | Playwright, axe | Nightly on `main`, on demand, and before each tag |
| Load | k6 | Before each tag from I4 |
| Stress and soak | k6 | Before the final tag |

Failing tests fail the job, and the failed job blocks the merge through the required status checks.

**TDD (T6):** business rules (pass validity and activation, pricing, deposit, overtime, booking hold, battery threshold, hub capacity) are developed test first. The failing test is committed before the implementation, and merge commits keep that order visible in the history of `dev` and `main`.

**BDD (T1):** the acceptance criteria of core stories are Gherkin scenarios, written at grooming and automated with Cucumber (section 5.3).

### 5.2 Developer-facing tests (unit, integration, API)

**Unit tests (open box)**

- Required for all business logic: domain classes, domain services and their rules, including boundary values (for example, battery at 19 % and 20 %, trip at 3 h and 3 h 1 min).
- Dependencies are mocked only at module or adapter boundaries; domain objects are used for real.
- Frontend: components with logic and custom hooks, tested with React Testing Library; the API is mocked.
- Simulator: message generation and fault scenarios.

**Integration tests**

- Named `*IT.java` and run by Maven Failsafe in the `verify` phase, separately from unit tests run by Surefire.
- **Persistence:** repositories and Flyway migrations against PostgreSQL in Testcontainers, never an in-memory database.
- **REST API:** controllers with the full Spring context and REST Assured, including authorization per role and error responses. `RoleAccessMatrixIT` (RIA-86) calls every endpoint with a real Keycloak token for each role and checks allowed, 403 and 401, including tokens with edited claims or signed by another key; it fails when an endpoint is missing from its matrix, so every new endpoint must declare its roles there.
- **Simulator adapter:** against Mosquitto in Testcontainers: telemetry messages update vehicle state; commands (lock, unlock) are published correctly.
- **Simulator contract:** the MQTT message formats are defined as JSON Schemas in the repository. The simulator's tests validate what it emits, and the backend's tests validate what the adapter accepts. A replacement simulator only has to meet the same schemas.
- **Payment adapter:** against [WireMock](https://wiremock.org/) stubs of the Stripe API, so CI needs no network or Stripe key ([ADR 0008](../../architecture/adr/0008-stripe-test-mode.md)). The stubs cover success and the failures the adapter must classify: transient (network error, 5xx, rate limit) and permanent (card declined, `authentication_required`). stripe-mock was not chosen because it cannot return these failures. The MVP uses no webhooks. Stripe test mode is used in staging and end-to-end tests, which also catch stubs that drift from the real API.

### 5.3 Acceptance testing

- **Which stories:** every core story, that is, every story of the common MVP core.
- **Who and when:** the developer drafts the Gherkin scenarios with the Product Owner at grooming; the Product Owner approves them before the story enters a sprint.
- **Automation:** Cucumber JVM runs the scenarios against the REST API, with REST Assured, on the application started with Testcontainers. Scenarios are tagged with the story key and the business rules they cover (`@RIA-31 @BR1`).
- **Web UI:** Playwright with TypeScript drives the core use cases in a browser against the Compose stack: register with a stay code, buy a pass, book and start a trip, end it at a hub, open a maintenance ticket, view the KPIs. These tests do not use Cucumber; they cover the UI path, while Cucumber covers the rules.
- **Xray:** see section 2.3.

### 5.4 Non-functional testing

**Performance (T5, T10).** k6 scripts in the repository test the book-and-start flow, the most critical one for guests:

| Scenario | Load | Thresholds | When |
|---|---|---|---|
| Smoke | 1 virtual user, 1 minute | No errors | Before each load run |
| Load | Ramp to 50 virtual users, 10 minutes | p95 < 500 ms, errors < 1 % | Before each tag from I4 |
| Stress | Ramp until failure | Find the breaking point; recovery after load | Before the final tag |
| Soak | 30 virtual users, 1 hour | Stable latency and memory | Before the final tag |

A threshold miss fails the k6 run. Results are kept as workflow artifacts and summarized here.

**Workload model.** The load must stress the service, not the business rules: with about forty e-bikes and one bike per pass, fifty users booking at once would mostly get legitimate rejections, counted as errors. The k6 runs therefore use:

- a dedicated `loadtest` seed with enough guests, passes, hubs and available vehicles for every virtual user to book its own vehicle;
- one guest and pass per virtual user, logged in during setup; each virtual user refreshes its access token with the refresh token before it expires (Keycloak's default lifespan is 5 minutes, shorter than the load and soak runs), and token requests carry their own tag, so the thresholds apply only to the trip flow;
- a cycle of book, start, end, with a think time of a few seconds, so each vehicle returns to a hub and can be booked again;
- a database reset from the seed before each run, so runs are comparable.

The 50 virtual users stand for a peak above the expected demand of three hotels, to leave margin. The runs target staging from a GitHub-hosted runner, so the load generator does not share the VM with the system under test. If staging does not accept connections from outside the university (open risk in architecture section 13), k6 runs from the self-hosted runner or another host inside the network instead, and the results say so, since a generator on the same VM competes with the system for resources.

**Accessibility (T8).** axe runs inside the Playwright suite on the core pages in both languages, with zero `serious` or `critical` violations allowed.

### 5.5 Other test techniques

**Pairwise testing (T7).** Where several inputs interact, PICT generates the combinations: for example, pass type × pass state at trip start × expiry during the trip × check-out during the trip × trip duration around the 3 h limit. The PICT models are kept in the repository, and their output feeds JUnit parameterized tests.

- **Constraints:** the models declare impossible combinations with PICT constraints (for example, a stay pass has no expiry separate from check-out), so no generated case tests a state the domain cannot reach.
- **Expected results:** each generated row needs its expected charge and outcome. These are computed by hand from the business rules and stored next to the generated rows, reviewed by the Product Owner; the test never computes them with the code under test.

**GBFS contract test (T9).** If the GBFS extension is adopted, the public feed is validated against the official GBFS v3.0 JSON schemas in an integration test.

### 5.6 Exploratory testing

From I3, each member runs one 60-minute session per iteration, guided by a written charter (for example, "explore trip end when the hub is full, to find wrong charges"). Notes are kept in the story or a Jira task. Each defect found becomes a Jira bug, and each gap in the automated tests becomes a new test.

## 6. AI and agents in quality engineering

### 6.1 Team policy for the use of AI

The team uses AI assistants and agents on purpose, for production code, tests, documentation and reviews. A human is accountable for every change, whoever or whatever wrote it.

**Do**

- Use AI to draft code, tests and documentation, then read and understand all of it before committing. You must be able to explain any line you merge.
- Give agents the repository instructions ([`CLAUDE.md`](../../../CLAUDE.md)), so their output follows this manual.
- Declare AI use in every pull request: the AI label (section 6.2) and the template checkbox.
- Treat AI review findings as suggestions: decide on each one and record the decision with a reason.
- Report AI mistakes you catch (wrong code, invented APIs, weak tests) in the pull request, so they feed the critical analysis (section 6.7).

**Don't**

- Never let an agent merge, approve a pull request or push to `dev` or `main`.
- Never give AI services secrets, credentials, tokens or personal data. Use synthetic data only.
- Never weaken or delete a test, or lower a quality gate threshold, to make AI-generated code pass.
- Never merge generated code that nobody on the team has reviewed line by line.
- Never let an agent move a Jira story to *Done*, mark a SonarQube security hotspot as reviewed, accept or close an issue as *won't fix*, or change the quality gate, the quality profile or the coverage exclusions (section 6.4).

**Advice for a newcomer:** ask the agent for small, reviewable changes tied to one story; ask it to write the test first for business rules; and check every library call it suggests against the official documentation.

### 6.2 Declaration of AI use

Each pull request declares its own AI use with exactly one label: `ai:none`, `ai:assisted` (AI helped, a person wrote and checked the change) or `ai:generated` (an agent produced the change from a specification, section 6.5). The `Exactly one AI label` check ([`ai-label.yml`](../../../.github/workflows/ai-label.yml)) is required on `dev` and `main`, so a pull request without a valid label cannot be merged. The labels are counted for the critical analysis (section 6.7).

The tools themselves are declared once for the whole project, updated as tools are added.

| Tool or agent (and model) | Member | Used for | How the output was verified |
|---|---|---|---|
| Claude Code (Claude Opus) | All | Drafting the architecture proposal, ADRs, report sections (including Product Specification sections 1 and 2, RIA-16) and this manual; setting up the CI and AI review workflows; the frontend walking skeleton (RIA-13); production code and tests (RIA-50, RIA-51, RIA-68) | Reviewed and edited by the author, then reviewed in a pull request by another member; code is also checked by its tests, CI and the quality gate |
| Claude Code GitHub Action (`anthropics/claude-code-action`) | All | Advisory review of non-draft pull requests (practice AI3) | Each finding accepted or rejected by the author, and checked by the human reviewer |
| Atlassian Rovo MCP server, used from Claude Code | All | Reading stories, acceptance criteria and Xray Tests from Jira; commenting on stories (section 6.4) | Comments are read by the story's owner; status changes to *Done* stay with the Product Owner |
| Claude (claude.ai, Claude Opus) | Maria Mané | Fleet simulator and MQTT contracts (RIA-60), frontend translation structure (RIA-74), register a bike (RIA-57), find available bikes (RIA-53), the I2 report updates and PDFs (RIA-17, RIA-75) and summaries of pull request reviews | Builds, unit and integration tests run locally before each commit; review in each pull request, with the AI review findings recorded |
| SonarQube MCP server and `sonar-*` skills, used from Claude Code | All | Reading the quality gate, issues, coverage gaps and duplications of a pull request; proposing fixes (section 6.4) | Each fix goes through the normal pull request review; the gate is re-run by CI |

*Each member adds the tools they use. All four members use the tools above; a tool used by only some members names them in the* Member *column.*

### 6.3 Agent instructions and coordination

**Instructions.** The agent instructions are versioned in the repository: [`AGENTS.md`](../../../AGENTS.md) points to [`CLAUDE.md`](../../../CLAUDE.md), which covers the domain, the architecture constraints, the Git and Jira rules, the code style, the definition of done, the expected tests, the quality and CI rules and the AI rules. It also lists the backend and frontend build and test commands (the simulator commands were added with RIA-60) and the limits of section 6.4 on what agents may do in Jira and SonarQube.

The QA Engineer maintains them. A pull request that changes a practice in this manual must update `CLAUDE.md` in the same pull request; the review checklist (section 3.2) checks it.

**Coordination (AI6).** Agent-generated stories use three agents with separate roles, defined as `test-author.md`, `implementer.md` and `reviewer.md` in `.claude/agents/` (added in RIA-73 and first used for RIA-56), with a human checkpoint after each handoff. Each definition lists the tools the agent may use, including its Jira and SonarQube access:

| Step | Agent | Input | Output | Human checkpoint |
|---|---|---|---|---|
| 1 | Test author | Approved Gherkin scenarios or OpenSpec proposal, read from the Jira story | Step definitions, unit tests and integration tests that fail | Developer checks that the tests express the criteria, then commits them |
| 2 | Implementer | Failing tests; SonarQube issues and coverage gaps on the branch | Implementation that makes them pass, without changing the tests | Developer reviews the diff and runs CI |
| 3 | Reviewer | Pull request diff, quality gate status | Review findings | Developer records findings; another member approves |

The implementer must not edit the tests written in step 1; any needed change goes back to step 1.

**How the roles are enforced.** The developer commits each agent step with a trailer naming the agent (`Agent: test-author` or `Agent: implementer`). On a pull request labelled `ai:generated`, the [`Agent boundaries`](../../../.github/workflows/agent-boundaries.yml) check fails when an implementer commit touches `backend/src/test/` or a test-author commit touches `backend/src/main/`. The reviewer agent's tools allow only read-only commands, so it cannot comment, approve, merge or push. The agents need the claude.ai Atlassian connector for Jira; without it, the test author stops instead of guessing the criteria.

### 6.4 Agent integration with Jira and SonarQube

Agents work from the same sources as people: the story in Jira and the analysis in SonarQube. Each member connects Claude Code to them with their own account, so an agent can do only what that member could do, and every action is logged under the member's name.

| Integration | How | What the agent may do | What stays human |
|---|---|---|---|
| Jira (project `RIA`) and Xray | Atlassian Rovo MCP server, authorized by each member with OAuth | Read a story, its acceptance criteria, linked Gherkin and Xray Tests before writing code or tests; search the backlog with JQL; comment on the story with the pull request link and a summary; move it from *To do* to *In progress* or *In review* | Creating or splitting stories, estimates, sprint planning, accepting a story (*Done*), and approving Gherkin scenarios (Product Owner) |
| SonarQube Cloud | SonarQube MCP server and the `sonar-*` skills, with a personal token of the member, kept in the local configuration, never in the repository or a prompt | Read the quality gate status and its conditions for a branch or pull request; list new issues, coverage gaps and duplications; explain a rule; propose a fix for an issue in code | Marking a security hotspot as reviewed, accepting an issue or closing it as *false positive* or *won't fix*, and changing the quality gate, quality profile or coverage exclusions |

How they fit the workflow:

- **Before coding**, the agent reads the story from Jira instead of a copy pasted into the prompt, so the tests follow the acceptance criteria approved by the Product Owner. Gherkin scenarios stay in the repository (section 2.3); the agent never edits them in Jira.
- **While coding**, when the quality gate fails on a pull request, the agent lists the new issues and uncovered lines and proposes fixes. The fix is a commit like any other, and CI runs the gate again: the agent cannot make the gate pass by changing it.
- **At review**, the reviewer agent (section 6.3) reads the gate status through the pull request's checks (`gh pr checks`), with the diff, and the human reviewer still reviews every hotspot (review checklist, section 3.2).
- **Data:** stories and code are synthetic and fictional (section 3.1), so sending them to the AI service exposes no personal data. Tokens are never pasted into prompts or committed.

*Evidence (to be linked in I2): a Jira story with an agent comment that links its pull request, and a pull request where an agent fixed a SonarQube issue that failed the gate.*

### 6.5 Specification-driven generation

Two stories, chosen by the Product Owner from the core stories, are generated by agents from their specification and delivered as pull requests:

- **Story 1 (I2):** RIA-56 Register a hub, from its approved Gherkin scenarios ([`RIA-56-register-hub.feature`](../../../backend/src/test/resources/features/RIA-56-register-hub.feature)), through the agent flow of section 6.3: [PR #35](https://github.com/TQS-RiaRide/RIARide/pull/35), with its human review and follow-up fixes in [PR #43](https://github.com/TQS-RiaRide/RIARide/pull/43).
- **Story 2 (I3):** from an [OpenSpec](https://github.com/Fission-AI/OpenSpec) change proposal, reviewed by the team before generation.

For each, this section will link the specification, the agent's pull request and its human review, and record what had to be corrected and why.

### 6.6 Agents in the CI pipeline

- **AI review (AI3):** [`ai-review.yml`](../../../.github/workflows/ai-review.yml) reviews non-draft pull requests from this repository when they are opened, reopened or marked ready for review. It skips Dependabot and fork pull requests, and it does not run on each push; a new review is requested by re-running the job.
- **PR summary (AI7):** a comment summarizing the change and its risk, posted when a pull request is opened.
- **Failure triage (AI7):** when CI fails on a pull request, an agent reads the logs and posts a comment with the likely cause and a suggested fix.

All agent jobs have permission only to read the code and comment on pull requests. They cannot approve, request changes, push or merge, and branch protection requires a human approval.

### 6.7 Critical analysis of AI's contribution

*Written in I5*, from data collected on every pull request from now on:

- **Share of AI-produced changes:** each pull request has one label, `ai:none`, `ai:assisted` or `ai:generated`, counted with `gh pr list --label`. The `AI label` check ([`ai-label.yml`](../../../.github/workflows/ai-label.yml)) fails a pull request with none of the three, more than one, or an unknown `ai:*` label; it is a required check in the branch protection of `dev` and `main`, so it blocks the merge. Dependabot pull requests get `ai:none` automatically. The earlier pull requests that have no label (#3 to #14, #17 and #20) will be labelled by the QA Engineer from their template declarations before the I5 analysis.
- **AI review findings:** accepted and rejected, by severity, from the findings tables. Each row starts with the severity (`HIGH: ...`), so [`tools/ai-review-findings`](../../../tools/ai-review-findings/README.md) can count them, in total and per `ai:*` label. Rows recorded before this convention (RIA-108) have no severity and are counted as `UNRATED`.
- **Agent actions in Jira and SonarQube:** agent comments on stories and fixes of SonarQube issues proposed by agents (section 6.4), and how many of those fixes needed human rework.
- **Defects and rework traced to AI:** Jira bugs and follow-up pull requests that name the original pull request, and quality gate failures on AI-labelled pull requests.

The labels are self-declared, and most changes will be `ai:assisted`, so the counts alone say little. The analysis compares groups: quality gate failures, review rounds and later defects for `ai:generated` against `ai:none` pull requests.

The analysis closes with what the team would do differently.
