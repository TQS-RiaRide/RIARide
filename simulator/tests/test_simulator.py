import json
import random
from datetime import timedelta

import pytest

from riaride_simulator.fleet import LockState
from riaride_simulator.simulator import Simulator
from riaride_simulator.topics import STATUS_TOPIC
from tests.conftest import HUB_PRAIA, HUB_SALINAS, command

TELEMETRY_101 = "riaride/vehicles/EB-101/telemetry"
COMMANDS_101 = "riaride/vehicles/EB-101/commands"
EVENTS_101 = "riaride/vehicles/EB-101/events"


def test_start_announces_online_retained_and_publishes_every_vehicle(simulator, publisher):
    simulator.start()

    topic, payload, retain = publisher.published[0]
    assert (topic, payload["status"], retain) == (STATUS_TOPIC, "ONLINE", True)
    assert len(publisher.on(TELEMETRY_101)) == 1
    assert len(publisher.on("riaride/vehicles/EB-102/telemetry")) == 1


def test_tick_advances_time_since_the_last_tick(simulator, publisher, clock):
    simulator.start()
    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    clock.advance(minutes=10)
    simulator.tick()

    latest = publisher.on(TELEMETRY_101)[-1]
    assert latest["batteryPct"] == 76
    assert latest["emittedAt"] == "2026-11-20T10:10:00.000Z"


def test_tick_before_start_does_not_change_the_fleet(simulator, publisher):
    simulator.tick()

    assert publisher.on(TELEMETRY_101)[-1]["batteryPct"] == 80


def test_unlock_answers_at_once_with_unlocked_telemetry(simulator, publisher):
    simulator.start()

    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    latest = publisher.on(TELEMETRY_101)[-1]
    assert latest["lockState"] == "UNLOCKED"
    assert latest["hubId"] is None


def test_lock_at_a_hub_docks_the_vehicle_there(simulator, publisher):
    simulator.start()
    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    simulator.handle_command(COMMANDS_101, command("LOCK", hub_id=HUB_SALINAS))

    latest = publisher.on(TELEMETRY_101)[-1]
    assert latest["lockState"] == "LOCKED"
    assert latest["hubId"] == HUB_SALINAS


def test_lock_at_an_unknown_hub_locks_in_place(simulator, publisher):
    simulator.start()
    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    simulator.handle_command(COMMANDS_101, command("LOCK", hub_id="unknown-hub"))

    latest = publisher.on(TELEMETRY_101)[-1]
    assert latest["lockState"] == "LOCKED"
    assert latest["hubId"] is None


def test_repeated_command_is_applied_once(simulator, publisher, fleet):
    simulator.start()
    unlock = command("UNLOCK", message_id="6f1c2a10-52b4-4a8e-9a31-1f0f7a1d9c11")
    simulator.handle_command(COMMANDS_101, unlock)
    simulator.handle_command(COMMANDS_101, command("LOCK", hub_id=HUB_PRAIA))

    simulator.handle_command(COMMANDS_101, unlock)

    assert fleet.vehicles["EB-101"].lock_state is LockState.LOCKED
    assert len(publisher.on(TELEMETRY_101)) == 3


VALID = {
    "messageId": "6f1c2a10-52b4-4a8e-9a31-1f0f7a1d9c11",
    "emittedAt": "2026-11-20T10:00:00Z",
    "command": "UNLOCK",
}


def _with(**changes) -> bytes:
    payload = {**VALID, **changes}
    return json.dumps({k: v for k, v in payload.items() if v is not ...}).encode()


@pytest.mark.parametrize(
    "payload",
    [
        b"not json",
        b"\xff\xfe",
        b"[]",
        _with(messageId="not-a-uuid"),
        _with(messageId=int("6f1c2a1052b44a8e9a311f0f7a1d9c11", 16)),
        _with(messageId="{6f1c2a10-52b4-4a8e-9a31-1f0f7a1d9c11}"),
        _with(messageId="urn:uuid:6f1c2a10-52b4-4a8e-9a31-1f0f7a1d9c11"),
        _with(messageId="6f1c2a1052b44a8e9a311f0f7a1d9c11"),
        _with(emittedAt=...),
        _with(emittedAt="yesterday"),
        _with(emittedAt="2026-11-20"),
        _with(emittedAt="2026-11-20T10:00:00"),
        _with(emittedAt="2026-13-20T10:00:00Z"),
        _with(command="EXPLODE"),
        _with(command="LOCK", hubId=""),
        _with(extra=1),
    ],
)
def test_invalid_commands_are_ignored(simulator, publisher, fleet, payload):
    simulator.start()
    published_before = len(publisher.published)

    simulator.handle_command(COMMANDS_101, payload)

    assert fleet.vehicles["EB-101"].lock_state is LockState.LOCKED
    assert len(publisher.published) == published_before


def test_the_same_uuid_in_upper_case_is_a_repeated_command(simulator, publisher, fleet):
    simulator.start()
    simulator.handle_command(COMMANDS_101, command("UNLOCK", message_id=VALID["messageId"]))
    simulator.handle_command(COMMANDS_101, command("LOCK", hub_id=HUB_PRAIA))

    simulator.handle_command(COMMANDS_101, command("UNLOCK", message_id=VALID["messageId"].upper()))

    assert fleet.vehicles["EB-101"].lock_state is LockState.LOCKED


def test_command_with_another_offset_is_accepted(simulator, fleet):
    simulator.start()

    simulator.handle_command(
        COMMANDS_101, command("UNLOCK", emitted_at="2026-11-20T10:00:00.250+00:00")
    )

    assert fleet.vehicles["EB-101"].lock_state is LockState.UNLOCKED


# BR13: the platform gives up on an unlock after 30 s, so an older command is stale.
def test_command_at_the_maximum_age_is_applied(simulator, fleet, clock):
    simulator.start()
    clock.advance(seconds=30)

    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    assert fleet.vehicles["EB-101"].lock_state is LockState.UNLOCKED


def test_command_older_than_the_maximum_age_is_ignored(simulator, publisher, fleet, clock):
    simulator.start()
    published_before = len(publisher.published)
    clock.advance(seconds=31)

    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    assert fleet.vehicles["EB-101"].lock_state is LockState.LOCKED
    assert len(publisher.published) == published_before


def test_maximum_command_age_is_configurable(fleet, publisher, clock, ids):
    simulator = Simulator(
        fleet, publisher, clock, random.Random(1), new_id=ids, max_command_age=timedelta(seconds=5)
    )
    simulator.start()
    clock.advance(seconds=6)

    simulator.handle_command(COMMANDS_101, command("UNLOCK"))

    assert fleet.vehicles["EB-101"].lock_state is LockState.LOCKED


@pytest.mark.parametrize("age", [timedelta(0), timedelta(seconds=-1)])
def test_maximum_command_age_must_be_positive(fleet, publisher, clock, age):
    with pytest.raises(ValueError):
        Simulator(fleet, publisher, clock, random.Random(1), max_command_age=age)


@pytest.mark.parametrize(
    "topic",
    ["riaride/vehicles/EB-999/commands", "riaride/vehicles//commands", "other/topic"],
)
def test_commands_for_unknown_vehicles_are_ignored(simulator, publisher, topic):
    simulator.start()
    published_before = len(publisher.published)

    simulator.handle_command(topic, command("UNLOCK"))

    assert len(publisher.published) == published_before


def test_raise_fault_publishes_one_event_with_its_own_message_id(simulator, publisher):
    simulator.raise_fault("EB-101", "BRAKES")
    simulator.raise_fault("EB-101", "BRAKES")

    events = publisher.on(EVENTS_101)
    assert [e["faultCode"] for e in events] == ["BRAKES", "BRAKES"]
    assert events[0]["messageId"] != events[1]["messageId"]


def test_raise_fault_for_unknown_vehicle_fails(simulator):
    with pytest.raises(KeyError):
        simulator.raise_fault("EB-999", "BRAKES")


def test_random_faults_follow_the_probability(fleet, publisher, clock, ids):
    always = Simulator(fleet, publisher, clock, random.Random(7), fault_probability=1.0, new_id=ids)

    always.tick()

    assert len(publisher.on(EVENTS_101)) == 1
    assert len(publisher.on("riaride/vehicles/EB-102/events")) == 1


def test_no_faults_with_probability_zero(simulator, publisher):
    for _ in range(50):
        simulator.tick()

    assert publisher.on(EVENTS_101) == []


@pytest.mark.parametrize("probability", [-0.1, 1.1])
def test_fault_probability_must_be_a_probability(fleet, publisher, clock, probability):
    with pytest.raises(ValueError):
        Simulator(fleet, publisher, clock, random.Random(1), fault_probability=probability)


def test_fleet_is_exposed_for_inspection(simulator, fleet):
    assert simulator.fleet is fleet
