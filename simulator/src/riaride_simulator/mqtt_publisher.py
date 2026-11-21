"""paho-mqtt wiring: the only module that knows about the MQTT library."""

from __future__ import annotations

import json
import logging
from collections.abc import Callable

import paho.mqtt.client as mqtt

from riaride_simulator import messages, topics
from riaride_simulator.config import Config

log = logging.getLogger(__name__)

QOS = 1  # ADR 0006: QoS 1 in both directions; consumers tolerate duplicates


class MqttPublisher:
    """Publishes JSON messages and delivers commands to a handler."""

    def __init__(self, config: Config, on_command: Callable[[str, bytes], None]) -> None:
        self._on_command = on_command
        self._client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2,
            client_id="riaride-simulator",
            clean_session=False,
        )
        if config.mqtt_username is not None:
            self._client.username_pw_set(config.mqtt_username, config.mqtt_password)
        # The broker publishes OFFLINE if the simulator disappears without notice.
        self._client.will_set(
            topics.STATUS_TOPIC, json.dumps(messages.status(False)), qos=QOS, retain=True
        )
        self._client.on_connect = self._handle_connect
        self._client.on_message = self._handle_message
        self._client.reconnect_delay_set(min_delay=1, max_delay=30)
        self._host = config.mqtt_host
        self._port = config.mqtt_port

    def connect(self) -> None:
        log.info("Connecting to MQTT broker %s:%s", self._host, self._port)
        self._client.connect(self._host, self._port, keepalive=30)
        self._client.loop_start()

    def disconnect(self) -> None:
        # A clean shutdown sends OFFLINE itself, since the will is only sent on a lost connection.
        self.publish(topics.STATUS_TOPIC, messages.status(False), retain=True)
        self._client.loop_stop()
        self._client.disconnect()

    def publish(self, topic: str, payload: dict, retain: bool = False) -> None:
        self._client.publish(topic, json.dumps(payload), qos=QOS, retain=retain)

    def _handle_connect(self, client, userdata, flags, reason_code, properties) -> None:
        if reason_code.is_failure:
            log.error("MQTT connection refused: %s", reason_code)
            return
        log.info("Connected; subscribing to %s", topics.COMMANDS_SUBSCRIPTION)
        client.subscribe(topics.COMMANDS_SUBSCRIPTION, qos=QOS)

    def _handle_message(self, client, userdata, message) -> None:
        self._on_command(message.topic, message.payload)
