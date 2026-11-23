"""MQTT topic names (architecture proposal, section 9)."""

PREFIX = "riaride"
STATUS_TOPIC = f"{PREFIX}/simulator/status"
COMMANDS_SUBSCRIPTION = f"{PREFIX}/vehicles/+/commands"


def telemetry_topic(vehicle_id: str) -> str:
    return f"{PREFIX}/vehicles/{vehicle_id}/telemetry"


def events_topic(vehicle_id: str) -> str:
    return f"{PREFIX}/vehicles/{vehicle_id}/events"


def vehicle_id_from_command_topic(topic: str) -> str | None:
    """Return the vehicle id of riaride/vehicles/{id}/commands, or None."""
    parts = topic.split("/")
    if len(parts) == 4 and parts[0] == PREFIX and parts[1] == "vehicles" and parts[3] == "commands":
        return parts[2] or None
    return None
