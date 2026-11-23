"""Builds and parses the MQTT messages defined in contracts/mqtt/."""

from __future__ import annotations

import re
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum

from riaride_simulator.fleet import Vehicle

FAULT_DESCRIPTIONS = {
    "BRAKES": "Brakes not responding",
    "FLAT_TYRE": "Flat tyre",
    "BATTERY": "Battery fault",
    "LOCK": "Lock mechanism jammed",
    "LIGHTS": "Lights not working",
}


# The forms the schema's "uuid" and "date-time" formats allow (RFC 4122, RFC 3339). uuid.UUID
# and datetime.fromisoformat alone accept more: braces, urn:uuid:, no dashes, a date with no
# time, a time with no offset.
_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", re.IGNORECASE)
_DATE_TIME = re.compile(
    r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?(Z|[+-]\d{2}:\d{2})", re.ASCII
)


class InvalidCommandError(ValueError):
    """A command message that does not match contracts/mqtt/command.schema.json."""


class CommandType(StrEnum):
    UNLOCK = "UNLOCK"
    LOCK = "LOCK"


@dataclass(frozen=True)
class Command:
    message_id: str  # canonical lower-case form, so one UUID written two ways is one id
    emitted_at: datetime
    command: CommandType
    hub_id: str | None


def timestamp(moment: datetime) -> str:
    """ISO 8601 in UTC with a Z suffix, to the millisecond."""
    if moment.tzinfo is None:
        raise ValueError("timestamps must be timezone-aware")
    return moment.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def new_message_id() -> str:
    return str(uuid.uuid4())


def telemetry(vehicle: Vehicle, emitted_at: datetime, message_id: str) -> dict:
    return {
        "messageId": message_id,
        "emittedAt": timestamp(emitted_at),
        "batteryPct": vehicle.battery_level,
        "lockState": vehicle.lock_state.value,
        "hubId": vehicle.hub_id,
        "position": {"lat": vehicle.position.lat, "lon": vehicle.position.lon},
    }


def fault_event(fault_code: str, emitted_at: datetime, message_id: str) -> dict:
    if fault_code not in FAULT_DESCRIPTIONS:
        raise ValueError(f"unknown fault code: {fault_code}")
    return {
        "messageId": message_id,
        "emittedAt": timestamp(emitted_at),
        "faultCode": fault_code,
        "description": FAULT_DESCRIPTIONS[fault_code],
    }


def status(online: bool, emitted_at: datetime | None = None) -> dict:
    message: dict = {"status": "ONLINE" if online else "OFFLINE"}
    if emitted_at is not None:
        message["emittedAt"] = timestamp(emitted_at)
    return message


def parse_command(payload: object) -> Command:
    """Check a decoded command against the contract and return it."""
    if not isinstance(payload, dict):
        raise InvalidCommandError("a command must be a JSON object")
    allowed = {"messageId", "emittedAt", "command", "hubId"}
    unknown = set(payload) - allowed
    if unknown:
        raise InvalidCommandError(f"unknown fields: {sorted(unknown)}")

    message_id = payload.get("messageId")
    if not isinstance(message_id, str) or not _UUID.fullmatch(message_id):
        raise InvalidCommandError("messageId must be a UUID")

    raw_emitted_at = payload.get("emittedAt")
    if not isinstance(raw_emitted_at, str):
        raise InvalidCommandError("emittedAt is required")
    not_a_date_time = "emittedAt must be an RFC 3339 date-time with an offset"
    if not _DATE_TIME.fullmatch(raw_emitted_at):
        raise InvalidCommandError(not_a_date_time)
    try:
        emitted_at = datetime.fromisoformat(raw_emitted_at)
    except ValueError as error:  # matches the pattern but is not a date, such as month 13
        raise InvalidCommandError(not_a_date_time) from error

    try:
        command = CommandType(payload.get("command"))
    except ValueError as error:
        raise InvalidCommandError("command must be UNLOCK or LOCK") from error

    hub_id = payload.get("hubId")
    if hub_id is not None and (not isinstance(hub_id, str) or not hub_id):
        raise InvalidCommandError("hubId must be a non-empty string or null")

    return Command(str(uuid.UUID(message_id)), emitted_at, command, hub_id)
