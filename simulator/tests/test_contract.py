"""Contract tests: every message the simulator emits matches contracts/mqtt/.

The backend's adapter tests validate the same schemas, so a replacement
simulator only has to meet them (QA Manual, section 5.2).
"""

from datetime import UTC, datetime

import pytest
from jsonschema import Draft202012Validator, FormatChecker

from riaride_simulator import messages
from tests.conftest import HUB_SALINAS, command


def validate(schema: dict, payload: dict) -> None:
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(payload)


def test_schemas_are_valid_json_schemas(schemas):
    assert set(schemas) == {"telemetry", "fault-event", "command", "simulator-status"}
    for schema in schemas.values():
        Draft202012Validator.check_schema(schema)


def test_every_published_message_matches_its_schema(simulator, publisher, schemas):
    simulator.start()
    simulator.handle_command("riaride/vehicles/EB-101/commands", command("UNLOCK"))
    simulator.tick()
    simulator.raise_fault("EB-101", "FLAT_TYRE")
    simulator.handle_command(
        "riaride/vehicles/EB-101/commands", command("LOCK", hub_id=HUB_SALINAS)
    )

    for topic, payload, _ in publisher.published:
        kind = topic.rsplit("/", 1)[-1]
        schema = {"telemetry": "telemetry", "events": "fault-event", "status": "simulator-status"}
        validate(schemas[schema[kind]], payload)


def test_offline_last_will_matches_the_status_schema(schemas):
    validate(schemas["simulator-status"], messages.status(False))


@pytest.mark.parametrize("hub_id", [None, HUB_SALINAS])
def test_commands_built_like_the_platform_match_the_schema(schemas, hub_id):
    import json

    validate(schemas["command"], json.loads(command("LOCK", hub_id=hub_id)))


def test_schema_rejects_telemetry_with_battery_above_100(schemas):
    from jsonschema import ValidationError

    message = {
        "messageId": "6f1c2a10-52b4-4a8e-9a31-1f0f7a1d9c11",
        "emittedAt": "2026-11-20T10:00:00Z",
        "batteryPct": 101,
        "lockState": "LOCKED",
        "hubId": None,
        "position": {"lat": 40.64, "lon": -8.65},
    }

    with pytest.raises(ValidationError):
        validate(schemas["telemetry"], message)


def test_timestamps_are_utc_with_z_suffix():
    moment = datetime(2026, 11, 20, 10, 15, 30, 123456, tzinfo=UTC)

    assert messages.timestamp(moment) == "2026-11-20T10:15:30.123Z"


def test_naive_timestamps_are_rejected():
    with pytest.raises(ValueError):
        messages.timestamp(datetime(2026, 11, 20, 10, 0))


def test_unknown_fault_codes_are_rejected():
    with pytest.raises(ValueError):
        messages.fault_event("ENGINE", datetime(2026, 11, 20, tzinfo=UTC), "id")
