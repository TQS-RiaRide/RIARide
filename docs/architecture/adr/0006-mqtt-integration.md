# ADR 0006: Bidirectional MQTT through Mosquitto

**Status:** proposed  
**Date:** 2026-11-10

## Context

The assignment requires one integration interface to the simulator, behind an adapter, and gives MQTT as an example. The team's epic 9 chose MQTT. Without commands, the simulator cannot know which bike is on a trip, and a demo would be inconsistent.

## Decision

Use MQTT through an Eclipse Mosquitto broker in both directions: telemetry and fault events from the simulator, `UNLOCK` and `LOCK` commands from the platform. QoS 1 applies to both, with idempotent consumers.

## Alternatives considered

- Kafka: durable replay, but oversized for simulated volume and awkward for per-vehicle commands.
- RabbitMQ: durable queues, but more to operate with no clear gain.
- HTTP webhooks: no broker, but couples the simulator to the platform's URL and leaves retries to us.
- WebSocket: point-to-point, with reconnection and delivery left to us.
- Telemetry only: simpler, but trips could contradict what the simulator emits.

## Consequences

- The protocol fits device messaging, and the broker is one small container.
- A last-will message reveals a stopped simulator.
- MQTT has no replay; history lives in PostgreSQL.
- Consumers must handle duplicates.
