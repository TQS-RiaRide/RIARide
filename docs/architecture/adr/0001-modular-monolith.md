# ADR 0001: Modular monolith

**Status:** proposed  
**Date:** 2026-11-10

## Context

RiaRide has ten epics, four developers and five weeks. The simulator must be a separate service, but nothing requires splitting the business logic.

## Decision

Build the backend as one Spring Boot deployable with one package per domain module. Use ports and adapters only at external integrations: vehicle telemetry, payments and the identity provider.

## Alternatives considered

- Layered monolith: quickest start, but global packages let dependencies spread and increase merge conflicts.
- Full hexagonal: strongest domain isolation, but too much mapping code for this time frame.
- Microservices: independent deployment is not needed, and distributed transactions would complicate one-bike-per-pass and hub capacity.

## Consequences

- One build, one deployment and local transactions.
- Team members can own modules and work in parallel.
- Boundaries need active enforcement, covered by ADR 0002.
