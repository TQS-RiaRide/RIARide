"""Builds and parses the MQTT messages defined in contracts/mqtt/."""

from __future__ import annotations

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


class InvalidCommandError(ValueError):
    """A command message that does not match contracts/mqtt/command.schema.json."""


class CommandType(StrEnum):
    UNLOCK = "UNLOCK"
    LOCK = "LOCK"


@dataclass(frozen=True)
class Command:
    message_id: str
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
    try:
        uuid.UUID(str(message_id))
    except ValueError as error:
        raise InvalidCommandError("messageId must be a UUID") from error

    emitted_at = payload.get("emittedAt")
    if not isinstance(emitted_at, str):
        raise InvalidCommandError("emittedAt is required")
    try:
        datetime.fromisoformat(emitted_at)
    except ValueError as error:
        raise InvalidCommandError("emittedAt must be an ISO 8601 date-time") from error

    try:
        command = CommandType(payload.get("command"))
    except ValueError as error:
        raise InvalidCommandError("command must be UNLOCK or LOCK") from error

    hub_id = payload.get("hubId")
    if hub_id is not None and (not isinstance(hub_id, str) or not hub_id):
        raise InvalidCommandError("hubId must be a non-empty string or null")

    return Command(str(message_id), command, hub_id)
