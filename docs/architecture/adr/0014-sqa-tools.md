# ADR 0014: Software quality assurance tools

**Status:** proposed  
**Date:** 2026-11-10

## Context

The assignment names most of the core tools: SonarQube and JaCoCo for static analysis and coverage, Cucumber for BDD, k6 for load testing and Xray for test management. It leaves three choices open: Selenium or Playwright for end-to-end tests, the dependency scanner, and how SonarQube is hosted.

The quality gate must block the merge, so SonarQube has to analyze a pull request before it is merged. The repository has three stacks: Java, TypeScript and Python.

## Decision

| Concern | Tool | Why |
|---|---|---|
| Static analysis and quality gate | SonarQube Cloud, with the `Sonar way` gate on new code | Covers all three stacks and reports the gate as a pull request status, with nothing to run on the university VM |
| Coverage | JaCoCo (backend), Vitest with LCOV (frontend), pytest-cov (simulator) | Each is the standard for its stack, and SonarQube imports all three reports |
| BDD | Cucumber JVM, run by Failsafe against the REST API | Required by the assignment; acceptance criteria stay readable by the Product Owner |
| End-to-end | Playwright with TypeScript, against the Compose stack, not on every build | Auto-waiting reduces flaky tests, traces make failures diagnosable, and it shares the frontend's language |
| Load | k6 on the book-and-start flow | Required by the assignment; thresholds fail the run when an SLO is missed |
| Dependency scanning on every build | OWASP Dependency-Check (backend), `npm audit` (frontend), `pip-audit` (simulator) | Each runs in CI and can fail the build on known vulnerabilities in its ecosystem |
| Dependency updates | Dependabot | Opens update pull requests for all three stacks and for GitHub Actions |
| Test management | Xray, importing Cucumber JSON and JUnit XML from CI | Required by the assignment; links tests to Jira stories |

## Alternatives considered

- SonarQube Cloud Free with a private repository: pull requests are analyzed only after they are merged into `main`, so the gate cannot block a merge.
- SonarQube Community Build, self-hosted: analyzes only the main branch, cannot block a merge either, and needs about 4 GB of RAM on a VM that is already tight.
- SonarQube Developer Edition or the Cloud Team plan: supports pull request analysis, but is paid.
- Selenium: supports more legacy browsers, but needs explicit waits and more setup, and offers no built-in trace viewer.
- Dependabot alone: the simplest option, but it is not tied to the build, while the assignment asks for a scan on every build.
- Trivy as a single scanner: fewer tools and also scans images, but is outside the assignment's examples. It can be added later for the container images.
- JMeter or Gatling for load testing: capable, but the assignment names k6.

## Consequences

- The team must confirm how pull requests get analyzed before the quality gate can block. The options are a public repository on the Free OSS plan, a SonarQube instance provided by the teaching staff, or a paid plan.
- The Free OSS plan announcement lists feature branch analysis but does not mention pull requests, so this must be tested on a real pull request first.
- A public repository requires checking its history for secrets and confirming that the course allows it.
- Dependency-Check needs an NVD API key, kept in GitHub Secrets, and caches its database in CI.
- Three scanners mean three reports to read, in exchange for coverage of every stack.
