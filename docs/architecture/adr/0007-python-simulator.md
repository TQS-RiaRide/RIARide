# ADR 0007: Python for the simulator

**Status:** proposed  
**Date:** 2026-11-10

## Context

The simulator is a separate service that publishes telemetry and reacts to commands. Its logic is small.

## Decision

Write it in Python with paho-mqtt, tested with pytest, and report coverage to SonarQube.

## Alternatives considered

- Java without Spring: reuses the Maven and JaCoCo toolchain, with more code for the same behavior.
- Node and TypeScript: shares the frontend language, with no clear advantage.

## Consequences

- Short, quick-to-change code.
- Using a different language shows that integration depends only on the MQTT contract.
- CI needs a separate job, and the project has a third language to maintain.
