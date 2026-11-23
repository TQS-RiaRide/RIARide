"""Integration tests (need Docker): the simulator against a real Mosquitto broker.

Run apart from the unit tests with `python -m pytest -m integration`. They cover the paho-mqtt
wiring that the unit tests replace with a fake publisher: connecting, the retained status,
an UNLOCK round trip, reconnecting after the broker restarts, and the OFFLINE status on stop.
"""

from __future__ import annotations

import json
import queue
import socket
import threading
import time
import uuid
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path

import paho.mqtt.client as mqtt
import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs

from riaride_simulator import topics
from riaride_simulator.__main__ import run
from riaride_simulator.config import Config

pytestmark = pytest.mark.integration

MOSQUITTO_IMAGE = "eclipse-mosquitto:2.0"
MOSQUITTO_CONF = Path(__file__).resolve().parents[2] / "mosquitto" / "mosquitto.conf"
TIMEOUT_SECONDS = 30
# A restarted container does not always close the old connection (Docker Desktop keeps the
# forwarded port open), so the simulator notices only through the keepalive, after about
# twice this. The default of 30 s would exceed TIMEOUT_SECONDS.
KEEPALIVE_SECONDS = 5
TELEMETRY_101 = topics.telemetry_topic("EB-101")
COMMANDS_101 = "riaride/vehicles/EB-101/commands"

Message = tuple[str, dict, bool]  # topic, payload, retained


def _free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


class Broker:
    def __init__(self, container: DockerContainer, host: str, port: int) -> None:
        self.container = container
        self.host = host
        self.port = port

    def restart(self) -> None:
        # The host port is fixed, so it survives the restart; persistence is off, so the
        # restarted broker has no retained messages.
        self.container.get_wrapped_container().restart()


class Watcher:
    """A test client that records every message on the topics it subscribes to."""

    def __init__(self, broker: Broker, *subscriptions: str) -> None:
        self.messages: queue.Queue[Message] = queue.Queue()
        self._subscribed = threading.Event()
        self._client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2, client_id=f"test-watcher-{uuid.uuid4()}"
        )
        self._client.on_connect = lambda client, *_: client.subscribe(
            [(topic, 1) for topic in subscriptions]
        )
        self._client.on_subscribe = lambda *_: self._subscribed.set()
        self._client.on_message = lambda _client, _userdata, message: self.messages.put(
            (message.topic, json.loads(message.payload), bool(message.retain))
        )
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while True:  # the broker may still be starting after a restart
            try:
                self._client.connect(broker.host, broker.port)
                break
            except OSError:
                if time.monotonic() > deadline:
                    raise
                time.sleep(0.5)
        self._client.loop_start()
        assert self._subscribed.wait(TIMEOUT_SECONDS), "the watcher could not subscribe"

    def publish(self, topic: str, payload: dict) -> None:
        self._client.publish(topic, json.dumps(payload), qos=1).wait_for_publish(TIMEOUT_SECONDS)

    def wait_for(self, matches: Callable[[Message], bool]) -> Message:
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while (remaining := deadline - time.monotonic()) > 0:
            try:
                message = self.messages.get(timeout=remaining)
            except queue.Empty:
                break
            if matches(message):
                return message
        pytest.fail("no matching message within the timeout")

    def close(self) -> None:
        self._client.disconnect()
        self._client.loop_stop()


@pytest.fixture
def broker():
    port = _free_port()
    container = (
        DockerContainer(MOSQUITTO_IMAGE)
        .with_bind_ports(1883, port)
        .with_volume_mapping(str(MOSQUITTO_CONF), "/mosquitto/config/mosquitto.conf", "ro")
    )
    with container:
        wait_for_logs(container, "running", timeout=TIMEOUT_SECONDS)
        yield Broker(container, container.get_container_host_ip(), port)


@pytest.fixture
def watchers():
    opened: list[Watcher] = []

    def open_watcher(broker: Broker, *subscriptions: str) -> Watcher:
        watcher = Watcher(broker, *subscriptions)
        opened.append(watcher)
        return watcher

    yield open_watcher
    for watcher in opened:
        watcher.close()


@pytest.fixture
def simulator(broker):
    """Runs the simulator as `python -m riaride_simulator` would; the event stops it."""
    stop = threading.Event()
    config = Config(
        mqtt_host=broker.host,
        mqtt_port=broker.port,
        mqtt_keepalive_seconds=KEEPALIVE_SECONDS,
        telemetry_interval_seconds=0.5,
        fault_probability=0.0,
        random_seed=1,
    )
    thread = threading.Thread(target=run, args=(config, stop), daemon=True)
    thread.start()
    yield stop, thread
    stop.set()
    thread.join(TIMEOUT_SECONDS)


def _status(expected: str) -> Callable[[Message], bool]:
    return lambda message: message[0] == topics.STATUS_TOPIC and message[1]["status"] == expected


def _retained_status(expected: str) -> Callable[[Message], bool]:
    status = _status(expected)
    return lambda message: status(message) and message[2]


def _wait_until_running(broker: Broker, watchers) -> None:
    watchers(broker, TELEMETRY_101).wait_for(lambda message: message[0] == TELEMETRY_101)


def test_connects_announces_online_retained_and_publishes_telemetry(broker, simulator, watchers):
    _wait_until_running(broker, watchers)

    # A client that subscribes later still gets the status, because it is retained.
    watchers(broker, topics.STATUS_TOPIC).wait_for(_retained_status("ONLINE"))


def test_unlock_round_trip(broker, simulator, watchers):
    watcher = watchers(broker, TELEMETRY_101)
    watcher.wait_for(lambda message: message[0] == TELEMETRY_101)

    watcher.publish(
        COMMANDS_101,
        {
            "messageId": str(uuid.uuid4()),
            "emittedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "command": "UNLOCK",
        },
    )

    watcher.wait_for(
        lambda message: message[0] == TELEMETRY_101 and message[1]["lockState"] == "UNLOCKED"
    )


def test_announces_online_again_after_the_broker_restarts(broker, simulator, watchers):
    _wait_until_running(broker, watchers)

    broker.restart()

    # The restarted broker lost the retained ONLINE, so any ONLINE now comes from the reconnect.
    # This client may subscribe before the simulator reconnects, and a live message reaches an
    # existing subscriber without the retain flag.
    watchers(broker, topics.STATUS_TOPIC).wait_for(_status("ONLINE"))
    # A client that subscribes after the reconnect gets it as the retained status.
    watchers(broker, topics.STATUS_TOPIC).wait_for(_retained_status("ONLINE"))


def test_publishes_offline_retained_when_stopped(broker, simulator, watchers):
    stop, thread = simulator
    _wait_until_running(broker, watchers)

    stop.set()
    thread.join(TIMEOUT_SECONDS)

    assert not thread.is_alive()
    watchers(broker, topics.STATUS_TOPIC).wait_for(_retained_status("OFFLINE"))
