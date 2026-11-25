from __future__ import annotations

import itertools
import json
import random
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

from riaride_simulator.fleet import Fleet
from riaride_simulator.simulator import Simulator

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts" / "mqtt"
# The test modules that verify a story, by module name. Only the messages the simulator exchanges
# with the platform (the MQTT contracts and the broker round trips) are evidence for RIA-58
# (Receive telemetry from the simulator); its configuration and fleet logic verify no story, so
# their results reach Xray unlinked (QA Manual 2.3).
STORY_BY_MODULE = {"test_contract": "RIA-58", "test_mqtt_broker": "RIA-58"}

HUB_PRAIA = "11111111-1111-4111-8111-111111111111"
HUB_SALINAS = "22222222-2222-4222-8222-222222222222"


@pytest.fixture(autouse=True)
def _xray_requirement(request, record_property):
    """Links a test to its story in the JUnit report CI imports into Xray (QA Manual 2.3)."""
    story = STORY_BY_MODULE.get(request.module.__name__.rsplit(".", 1)[-1])
    if story is not None:
        record_property("requirements", story)


class FakePublisher:
    def __init__(self) -> None:
        self.published: list[tuple[str, dict, bool]] = []

    def publish(self, topic: str, payload: dict, retain: bool = False) -> None:
        self.published.append((topic, payload, retain))

    def on(self, topic: str) -> list[dict]:
        return [payload for t, payload, _ in self.published if t == topic]


class FixedClock:
    def __init__(self, start: datetime) -> None:
        self.now = start

    def __call__(self) -> datetime:
        return self.now

    def advance(self, **kwargs: float) -> None:
        self.now += timedelta(**kwargs)


def fleet_data() -> dict:
    return {
        "hubs": [
            {"id": HUB_PRAIA, "name": "Marés Praia", "lat": 40.6405, "lon": -8.7538},
            {"id": HUB_SALINAS, "name": "Marés Salinas", "lat": 40.6510, "lon": -8.6820},
        ],
        "vehicles": [
            {"id": "EB-101", "hubId": HUB_PRAIA, "batteryPct": 80},
            {"id": "EB-102", "hubId": HUB_SALINAS, "batteryPct": 20},
        ],
    }


@pytest.fixture
def fleet() -> Fleet:
    return Fleet.from_dict(fleet_data())


@pytest.fixture
def publisher() -> FakePublisher:
    return FakePublisher()


@pytest.fixture
def clock() -> FixedClock:
    return FixedClock(datetime(2026, 11, 20, 10, 0, tzinfo=UTC))


@pytest.fixture
def ids():
    counter = itertools.count(1)
    return lambda: str(uuid.UUID(int=next(counter)))


@pytest.fixture
def simulator(fleet, publisher, clock, ids) -> Simulator:
    return Simulator(fleet, publisher, clock, random.Random(42), fault_probability=0.0, new_id=ids)


@pytest.fixture
def schemas() -> dict[str, dict]:
    return {
        path.name.removesuffix(".schema.json"): json.loads(path.read_text(encoding="utf-8"))
        for path in CONTRACTS.glob("*.schema.json")
    }


def command(
    name: str,
    hub_id: str | None = None,
    message_id: str | None = None,
    emitted_at: str = "2026-11-20T10:00:00Z",
) -> bytes:
    payload = {
        "messageId": message_id or str(uuid.uuid4()),
        "emittedAt": emitted_at,
        "command": name,
    }
    if hub_id is not None:
        payload["hubId"] = hub_id
    return json.dumps(payload).encode()
