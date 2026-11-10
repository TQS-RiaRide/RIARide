# ADR 0011: Docker Compose on a university VM

**Status:** proposed  
**Date:** 2026-11-10

## Context

Production must be described as code, deployed automatically from `main` and reachable by the teaching staff.

## Decision

Run the stack with Docker Compose on one university VM. GitHub Actions builds images, pushes them to GHCR and triggers `docker compose pull` and `up`. nginx exposes only port 443.

## Alternatives considered

- Cloud VM on student credits: public IP and SSH deploys, but account and credit management fall on the team.
- PaaS: less administration, but Compose no longer describes the real environment, MQTT is not HTTP, and free plans are too small.

## Consequences

- The same Compose description runs locally and in production.
- VM availability and reachability still need confirmation.
- If the VM is internal only, deployment needs a self-hosted runner.
- One host is a single point of failure, acceptable for this project.
