# ADR 0004: PostgreSQL as the only database

**Status:** proposed  
**Date:** 2026-11-10

## Context

The platform stores transactional data and per-vehicle telemetry. Several business rules depend on transactions and constraints. Telemetry volume is small because it is simulated.

## Decision

Use PostgreSQL for everything. Latest vehicle state is a set of columns on `vehicle`, and history goes to an append-only `telemetry_event` table. Flyway manages the schema and Testcontainers runs a real PostgreSQL in integration tests.

## Alternatives considered

- PostgreSQL with TimescaleDB: useful aggregates, little visible gain at this volume.
- PostgreSQL with InfluxDB: suits large scale, but adds a second database to operate and makes KPIs cross two stores.
- MySQL or MariaDB: viable, but lacks direct partial unique indexes, which enforce the active-trip and active-reservation rules.

## Consequences

- One data service to operate, back up and test.
- Partial unique indexes enforce concurrency rules.
- A much larger telemetry volume would need retention or partitioning.
