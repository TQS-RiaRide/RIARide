"""Configuration from environment variables."""

from __future__ import annotations

import math
import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

DEFAULT_FLEET_FILE = Path(__file__).resolve().parent / "fleet.json"


@dataclass(frozen=True)
class Config:
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    # paho sends a ping after this long without a reply from the broker and drops the
    # connection after as long again, so a dead connection is noticed within about twice this.
    mqtt_keepalive_seconds: int = 30
    mqtt_username: str | None = None
    mqtt_password: str | None = None
    fleet_file: Path = DEFAULT_FLEET_FILE
    telemetry_interval_seconds: float = 5.0
    fault_probability: float = 0.001
    command_max_age_seconds: float = 30.0
    random_seed: int | None = None
    log_level: str = "INFO"

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> Config:
        env = os.environ if env is None else env
        config = cls(
            mqtt_host=env.get("MQTT_HOST", cls.mqtt_host),
            mqtt_port=_int(env, "MQTT_PORT", cls.mqtt_port),
            mqtt_keepalive_seconds=_int(env, "MQTT_KEEPALIVE_SECONDS", cls.mqtt_keepalive_seconds),
            mqtt_username=env.get("MQTT_USERNAME") or None,
            mqtt_password=env.get("MQTT_PASSWORD") or None,
            fleet_file=Path(env.get("FLEET_FILE", str(DEFAULT_FLEET_FILE))),
            telemetry_interval_seconds=_float(
                env, "TELEMETRY_INTERVAL_SECONDS", cls.telemetry_interval_seconds
            ),
            fault_probability=_float(env, "FAULT_PROBABILITY", cls.fault_probability),
            command_max_age_seconds=_float(
                env, "COMMAND_MAX_AGE_SECONDS", cls.command_max_age_seconds
            ),
            random_seed=_int(env, "RANDOM_SEED", None),
            log_level=env.get("LOG_LEVEL", cls.log_level).upper(),
        )
        config.validate()
        return config

    def validate(self) -> None:
        if not 1 <= self.mqtt_port <= 65535:
            raise ValueError("MQTT_PORT must be between 1 and 65535")
        if not 1 <= self.mqtt_keepalive_seconds <= 65535:
            raise ValueError("MQTT_KEEPALIVE_SECONDS must be between 1 and 65535")
        # NaN fails every comparison and inf never elapses, so both are refused.
        if not _positive(self.telemetry_interval_seconds):
            raise ValueError("TELEMETRY_INTERVAL_SECONDS must be a positive number")
        if not _positive(self.command_max_age_seconds):
            raise ValueError("COMMAND_MAX_AGE_SECONDS must be a positive number")
        if not 0.0 <= self.fault_probability <= 1.0:
            raise ValueError("FAULT_PROBABILITY must be between 0 and 1")
        if (self.mqtt_username is None) != (self.mqtt_password is None):
            raise ValueError("set both MQTT_USERNAME and MQTT_PASSWORD, or neither")


def _positive(value: float) -> bool:
    return math.isfinite(value) and value > 0


def _int(env: Mapping[str, str], name: str, default: int | None) -> int | None:
    value = env.get(name)
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError as error:
        raise ValueError(f"{name} must be a whole number") from error


def _float(env: Mapping[str, str], name: str, default: float) -> float:
    value = env.get(name)
    if value is None or value == "":
        return default
    try:
        return float(value)
    except ValueError as error:
        raise ValueError(f"{name} must be a number") from error
