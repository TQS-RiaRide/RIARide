"""paho-mqtt wiring: the only module that knows about the MQTT library."""

from __future__ import annotations

import json
import logging
from collections.abc import Callable
from datetime import UTC, datetime

import paho.mqtt.client as mqtt

from riaride_simulator import messages, topics
from riaride_simulator.config import Config

log = logging.getLogger(__name__)

QOS = 1  # ADR 0006: QoS 1 in both directions; consumers tolerate duplicates
OFFLINE_PUBLISH_TIMEOUT_SECONDS = 5.0


class MqttPublisher:
    """Publishes JSON messages and delivers commands to a handler."""

    def __init__(
        self,
        config: Config,
        on_command: Callable[[str, bytes], None],
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ) -> None:
        self._on_command = on_command
        self._clock = clock
        # A persistent session keeps the commands sent during a short disconnection. Commands
        # older than COMMAND_MAX_AGE_SECONDS are dropped by the simulator (BR13).
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
        self._keepalive = config.mqtt_keepalive_seconds

    def connect(self) -> None:
        log.info("Connecting to MQTT broker %s:%s", self._host, self._port)
        self._client.connect(self._host, self._port, keepalive=self._keepalive)
        self._client.loop_start()

    def disconnect(self) -> None:
        # A clean disconnect discards the last will, so OFFLINE is sent first and the network
        # loop keeps running until the broker has acknowledged it.
        info = self._client.publish(
            topics.STATUS_TOPIC,
            json.dumps(messages.status(False, self._clock())),
            qos=QOS,
            retain=True,
        )
        try:
            info.wait_for_publish(timeout=OFFLINE_PUBLISH_TIMEOUT_SECONDS)
        except (RuntimeError, ValueError) as error:  # not connected, or the queue is full
            log.warning("Could not publish OFFLINE before disconnecting: %s", error)
        self._client.disconnect()
        self._client.loop_stop()

    def publish(self, topic: str, payload: dict, retain: bool = False) -> None:
        self._client.publish(topic, json.dumps(payload), qos=QOS, retain=retain)

    def _handle_connect(self, client, userdata, flags, reason_code, properties) -> None:
        if reason_code.is_failure:
            log.error("MQTT connection refused: %s", reason_code)
            return
        log.info("Connected; subscribing to %s", topics.COMMANDS_SUBSCRIPTION)
        client.subscribe(topics.COMMANDS_SUBSCRIPTION, qos=QOS)
        # Runs on every reconnect: the broker published the OFFLINE will when the connection
        # dropped, and a broker restart loses the retained status (persistence is off).
        client.publish(
            topics.STATUS_TOPIC,
            json.dumps(messages.status(True, self._clock())),
            qos=QOS,
            retain=True,
        )

    def _handle_message(self, client, userdata, message) -> None:
        self._on_command(message.topic, message.payload)
