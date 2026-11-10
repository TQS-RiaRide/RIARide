# ADR 0014: Software quality assurance tools

**Status:** proposed  
**Date:** 2026-11-10

## Context

The assignment names most of the core tools: SonarQube and JaCoCo for static analysis and coverage, Cucumber for BDD, k6 for load testing and Xray for test management. It leaves three choices open: Selenium or Playwright for end-to-end tests, the dependency scanner, and how SonarQube is hosted.

The quality gate must block the merge, so SonarQube has to analyze a pull request before it is merged. The repository has three stacks: Java, TypeScript and Python, and it is public on GitHub.

## Decision

| Concern | Tool | Why |
|---|---|---|
| Static analysis and quality gate | SonarQube Cloud on the free OSS plan, analyzed from GitHub Actions, with a quality gate on new code covering coverage, reliability, security and reviewed security hotspots | The OSS plan covers public repositories and includes unlimited branch and pull request analysis. The gate status appears on the pull request, there is no server to host, and all three stacks are covered |
| Coverage | JaCoCo (backend), Vitest with LCOV (frontend), pytest-cov (simulator) | Each is the standard for its stack, and SonarQube imports all three reports |
| BDD | Cucumber JVM, run by Failsafe against the REST API | Required by the assignment; acceptance criteria stay readable by the Product Owner |
| End-to-end | Playwright with TypeScript, against the Compose stack, not on every build | Auto-waiting reduces flaky tests, traces make failures diagnosable, and it shares the frontend's language |
| Load | k6 on the book-and-start flow | Required by the assignment; thresholds fail the run when an SLO is missed |
| Dependency scanning on every build | OWASP Dependency-Check (backend), `npm audit` (frontend), `pip-audit` (simulator) | Each runs in CI and can fail the build on known vulnerabilities in its ecosystem |
| Dependency updates | Dependabot | Opens update pull requests for all three stacks and for GitHub Actions |
| Test management | Xray, importing Cucumber JSON and JUnit XML from CI | Required by the assignment; links tests to Jira stories |

## Alternatives considered

- SonarQube Server, Developer Edition or higher: analyzes pull requests and reports the gate, but needs a licence and a host with about 4 GB of RAM that GitHub Actions can reach. It is the fallback if the repository has to become private.
- SonarQube Cloud Free plan: analyzes only the main branch, and pull requests only when they target it, with a limit of 50k lines of code for private projects. The OSS plan has no such restriction for public repositories.
- SonarQube Community Build: analyzes only the main branch, so the gate cannot block a merge.
- Coverage: the alternatives are tied to each language, and the chosen tools are the ones SonarQube imports directly, so no comparison changes the choice.
- BDD: Cucumber is required by the assignment; JBehave and Spock were not considered further.
- Selenium: supports more legacy browsers, but needs explicit waits and more setup, and offers no built-in trace viewer.
- Dependabot alone: the simplest option, but it is not tied to the build, while the assignment asks for a scan on every build.
- Trivy as a single scanner: fewer tools and also scans images, but is outside the assignment's examples. It can be added later for the container images.
- JMeter or Gatling for load testing: capable, but the assignment names k6.

## Consequences

- The repository must stay public. Seed and test data are already synthetic, and secrets stay in GitHub Secrets.
- The SonarQube Cloud organization must be on the OSS plan. This, and the gate on a pull request, must be confirmed on a real pull request in iteration I2.
- Analysis runs in GitHub Actions, not as automatic analysis, so the coverage reports produced by the build are imported. A SonarQube Cloud token is kept in GitHub Secrets.
- Pull requests come from branches of this repository. A pull request from a fork would not receive the token.
- The merge is blocked by protecting `main` and making the SonarQube status check required.
- Dependency-Check needs an NVD API key, kept in GitHub Secrets, and caches its database in CI.
- Three scanners mean three reports to read, in exchange for coverage of every stack.
