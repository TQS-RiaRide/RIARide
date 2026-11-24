from pathlib import Path

import pytest

from riaride_simulator.config import DEFAULT_FLEET_FILE, Config
from riaride_simulator.topics import vehicle_id_from_command_topic


def test_defaults_when_no_variables_are_set():
    config = Config.from_env({})

    assert (config.mqtt_host, config.mqtt_port) == ("localhost", 1883)
    assert config.fleet_file == DEFAULT_FLEET_FILE
    assert config.mqtt_username is None
    assert config.random_seed is None
    assert config.command_max_age_seconds == 30.0
    assert config.mqtt_keepalive_seconds == 30


def test_reads_every_variable():
    config = Config.from_env(
        {
            "MQTT_HOST": "mosquitto",
            "MQTT_PORT": "8883",
            "MQTT_KEEPALIVE_SECONDS": "10",
            "MQTT_USERNAME": "simulator",
            "MQTT_PASSWORD": "test-only",
            "FLEET_FILE": "/data/fleet.json",
            "TELEMETRY_INTERVAL_SECONDS": "2.5",
            "FAULT_PROBABILITY": "0.01",
            "COMMAND_MAX_AGE_SECONDS": "45",
            "RANDOM_SEED": "42",
            "LOG_LEVEL": "debug",
        }
    )

    assert config.mqtt_host == "mosquitto"
    assert config.mqtt_port == 8883
    assert config.mqtt_keepalive_seconds == 10
    assert config.mqtt_username == "simulator"
    assert config.fleet_file == Path("/data/fleet.json")
    assert config.telemetry_interval_seconds == 2.5
    assert config.fault_probability == 0.01
    assert config.command_max_age_seconds == 45.0
    assert config.random_seed == 42
    assert config.log_level == "DEBUG"


@pytest.mark.parametrize(
    "env",
    [
        {"MQTT_PORT": "abc"},
        {"MQTT_PORT": "0"},
        {"MQTT_KEEPALIVE_SECONDS": "0"},
        {"MQTT_KEEPALIVE_SECONDS": "1.5"},
        {"TELEMETRY_INTERVAL_SECONDS": "0"},
        {"TELEMETRY_INTERVAL_SECONDS": "fast"},
        {"TELEMETRY_INTERVAL_SECONDS": "nan"},
        {"TELEMETRY_INTERVAL_SECONDS": "inf"},
        {"TELEMETRY_INTERVAL_SECONDS": "-inf"},
        {"FAULT_PROBABILITY": "2"},
        {"FAULT_PROBABILITY": "nan"},
        {"COMMAND_MAX_AGE_SECONDS": "0"},
        {"COMMAND_MAX_AGE_SECONDS": "nan"},
        {"COMMAND_MAX_AGE_SECONDS": "inf"},
        {"MQTT_USERNAME": "simulator"},
    ],
)
def test_invalid_values_are_rejected(env):
    with pytest.raises(ValueError):
        Config.from_env(env)


@pytest.mark.parametrize(
    ("topic", "vehicle_id"),
    [
        ("riaride/vehicles/EB-101/commands", "EB-101"),
        ("riaride/vehicles/EB-101/telemetry", None),
        ("riaride/vehicles//commands", None),
        ("riaride/vehicles/EB-101/commands/extra", None),
    ],
)
def test_vehicle_id_is_read_from_the_command_topic(topic, vehicle_id):
    assert vehicle_id_from_command_topic(topic) == vehicle_id
