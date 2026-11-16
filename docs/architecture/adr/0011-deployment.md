# ADR 0011: Staging and production with Docker Compose on a university VM

**Status:** proposed  
**Date:** 2026-11-10, revised 2026-11-16 to match the QA Manual (section 4.2)

## Context

Production must be described as code, deployed automatically from `main` with representative data, and reachable by the teaching staff. Separate staging and production environments with a promotion step are a valued practice (D5). The university VM may not accept connections from GitHub.

## Decision

Run two Docker Compose stacks, staging and production, on one university VM. Both use the same Compose files; environment differences live in host-only `.env` files.

- A merge to `main` builds the images, scans them with Trivy, pushes them to GHCR and deploys them automatically to staging.
- An iteration tag (`iter1`, ..., `final_v1`) promotes the images already running in staging to production, after a manual approval in a protected GitHub Environment. Nothing is rebuilt.
- Deploy jobs run on a self-hosted GitHub Actions runner on the VM, which polls GitHub, so the VM needs no inbound connection from it. Only deploy jobs target the runner, by label, and only on `main` and tags through the protected Environment. Workflows from forks need maintainer approval, and the runner can reach only the Compose stacks.
- In each stack, nginx exposes only port 443.

## Alternatives considered

- One environment deployed from `main`: simpler and enough for D4, but a broken merge reaches the environment the teaching staff use, and D5 is lost.
- SSH deploys from GitHub-hosted runners: no runner to maintain, but the VM must accept SSH from the internet.
- Cloud VM on student credits: public IP and SSH deploys, but account and credit management fall on the team.
- PaaS: less administration, but Compose no longer describes the real environment, MQTT is not HTTP, and free plans are too small.

## Consequences

- The same Compose description runs locally, in staging and in production.
- `main` deploys automatically to staging (D4); production changes only through a tag and an approval (D5).
- The self-hosted runner on a public repository must stay restricted as described, or a pull request could run code on the VM.
- Two stacks on one VM double the memory needed. If the VM cannot hold both, monitoring is shared and staging stops between uses.
- VM availability, and public reachability for the teaching staff, still need confirmation.
- One host is a single point of failure, acceptable for this project.
