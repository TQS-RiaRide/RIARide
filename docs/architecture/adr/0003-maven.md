# ADR 0003: Maven as the build tool

**Status:** proposed  
**Date:** 2026-11-10

## Context

The project must run unit and integration tests separately and report coverage and quality to SonarQube.

## Decision

Use Maven with the wrapper checked in. Surefire runs unit tests, Failsafe runs integration tests, and JaCoCo produces coverage for SonarQube.

## Alternatives considered

- Gradle: faster incremental builds and shorter configuration, but integration tests need an extra test suite, and the team gains little at this project size.

## Consequences

- Standard plugins cover the required separation.
- The wrapper pins the Maven version for developers and CI.
- Configuration is more verbose.
