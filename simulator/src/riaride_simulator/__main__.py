"""Entry point: python -m riaride_simulator"""

from __future__ import annotations

import logging
import random
import signal
import threading
from datetime import UTC, datetime

from riaride_simulator.config import Config
from riaride_simulator.fleet import Fleet
from riaride_simulator.mqtt_publisher import MqttPublisher
from riaride_simulator.simulator import Simulator


def main() -> None:
    config = Config.from_env()
    logging.basicConfig(
        level=config.log_level, format="%(asctime)s %(levelname)s %(name)s: %(message)s"
    )
    fleet = Fleet.from_file(config.fleet_file)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())

    # Commands arrive on paho's network thread while ticks run on this one.
    lock = threading.Lock()
    simulator: Simulator

    def on_command(topic: str, payload: bytes) -> None:
        with lock:
            simulator.handle_command(topic, payload)

    publisher = MqttPublisher(config, on_command)
    simulator = Simulator(
        fleet,
        publisher,
        clock=lambda: datetime.now(UTC),
        rng=random.Random(config.random_seed),
        fault_probability=config.fault_probability,
    )
    publisher.connect()
    with lock:
        simulator.start()
    logging.getLogger(__name__).info(
        "Simulating %d vehicles at %d hubs", len(fleet.vehicles), len(fleet.hubs)
    )
    while not stop.wait(config.telemetry_interval_seconds):
        with lock:
            simulator.tick()
    publisher.disconnect()


if __name__ == "__main__":
    main()
