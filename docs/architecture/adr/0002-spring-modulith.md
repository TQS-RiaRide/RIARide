# ADR 0002: Spring Modulith for module boundaries

**Status:** proposed  
**Date:** 2026-11-10

## Context

A modular monolith degrades unless boundaries are checked. Side effects such as charging overtime must survive a crash after the trip commits.

## Decision

Use Spring Modulith. `ApplicationModules.verify()` runs as a test. Modules call each other's public API for queries and needed results, and publish events for side effects. The event publication registry persists events in PostgreSQL until listeners succeed.

## Alternatives considered

- ArchUnit with plain Spring events: familiar, but events are lost on a crash without a hand-written outbox.
- Direct calls only: easiest to trace, but couples `trips` to every side effect and invites cycles.

## Consequences

- Boundary violations fail the build, which is evidence for the QA Manual.
- Listeners must be idempotent because publications can be retried.
- The team has one more library to learn.
