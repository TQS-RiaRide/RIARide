# RiaRide fleet simulator

A minimal Python service that stands in for the e-bikes ([ADR 0006](../docs/architecture/adr/0006-mqtt-integration.md), [ADR 0007](../docs/architecture/adr/0007-python-simulator.md)). It talks to the platform only over MQTT, so a different simulator can replace it by following the same contract.

## What it does

- Every `TELEMETRY_INTERVAL_SECONDS`, publishes each vehicle's battery, lock state, hub and position.
- A docked, locked bike charges; an unlocked bike drains its battery and moves.
- Raises random faults (brakes, flat tyre, battery, lock, lights) with probability `FAULT_PROBABILITY` per vehicle and tick.
- Reacts to `UNLOCK` and `LOCK` commands, and answers each one at once with the vehicle's telemetry, which is how the platform confirms an unlock (BR13). Repeated commands (same `messageId`) are applied once; invalid ones, and commands older than `COMMAND_MAX_AGE_SECONDS`, are logged and ignored. The broker keeps the simulator's session while it is down, so without that limit an UNLOCK the platform gave up on (BR13) could be applied after a restart.
- Publishes `ONLINE` on `riaride/simulator/status` (retained) each time it connects, including reconnects, leaves `OFFLINE` as its last will, and publishes `OFFLINE` itself when it stops.

## MQTT contract

All messages use QoS 1. Their formats are the JSON Schemas in [`contracts/mqtt/`](../contracts/mqtt/), checked by this service's tests and, later, by the backend adapter's tests.

| Topic | Direction | Schema |
|---|---|---|
| `riaride/vehicles/{vehicleId}/telemetry` | Simulator to platform | `telemetry.schema.json` |
| `riaride/vehicles/{vehicleId}/events` | Simulator to platform | `fault-event.schema.json` |
| `riaride/vehicles/{vehicleId}/commands` | Platform to simulator | `command.schema.json` |
| `riaride/simulator/status` | Simulator to platform | `simulator-status.schema.json` |

`{vehicleId}` is the vehicle code (for example `EB-101`). `hubId` is the platform's hub id, so the demo fleet in [`fleet.json`](src/riaride_simulator/fleet.json) must use the same hub ids as the platform's seed data.

## Commands

Python 3.12 or later. From `simulator/`:

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
ruff check . && ruff format --check .     # lint and format check (ruff format . fixes)
python -m pytest                                  # unit tests
python -m pytest -m integration                   # integration tests against Mosquitto (needs Docker)
python -m pytest --cov --cov-report= && python -m pytest -m integration --cov --cov-append --cov-report=term --cov-report=xml   # both, with coverage for SonarQube
python -m pytest tests/test_simulator.py -k unlock          # a single test
pip-audit -r requirements.txt                     # dependency vulnerabilities
```

Run it with a local Mosquitto broker (from the repository root):

```bash
docker compose -f simulator/compose.yaml up --build
docker compose -f simulator/compose.yaml exec mosquitto mosquitto_sub -t 'riaride/#' -v
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `MQTT_HOST`, `MQTT_PORT` | `localhost`, `1883` | Broker address |
| `MQTT_KEEPALIVE_SECONDS` | `30` | MQTT keepalive; a dead connection is noticed after about twice this |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | none | Broker credentials; set both or neither |
| `FLEET_FILE` | bundled `fleet.json` | Hubs and vehicles to simulate |
| `TELEMETRY_INTERVAL_SECONDS` | `5` | Time between telemetry rounds |
| `FAULT_PROBABILITY` | `0.001` | Chance of a fault per vehicle and round |
| `COMMAND_MAX_AGE_SECONDS` | `30` | Commands older than this are ignored (BR13) |
| `RANDOM_SEED` | none | Fixes the random faults and movement, for repeatable demos |
| `LOG_LEVEL` | `INFO` | Python log level |

## Design

`simulator.py` holds the behaviour and depends only on a `Publisher` protocol, so it is unit-tested with a fake publisher, a fixed clock and a seeded random generator. `mqtt_publisher.py` is the only module that imports paho-mqtt; it and `run()` in `__main__.py` are covered by the integration tests in `tests/integration/`, which start Mosquitto with Testcontainers.

Run it with the rest of the local stack with `docker compose -f infra/compose.yaml up -d` (see [`infra/README.md`](../infra/README.md)).
