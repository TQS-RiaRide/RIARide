"""The simulation loop: advances the fleet and talks to the platform.

The simulator depends only on the `Publisher` protocol, not on paho-mqtt, so
its behaviour is unit-tested with a fake publisher, a fixed clock and a
seeded random generator.
"""

from __future__ import annotations

import json
import logging
import random
from collections import OrderedDict
from collections.abc import Callable
from datetime import datetime, timedelta
from typing import Protocol

from riaride_simulator import messages, topics
from riaride_simulator.fleet import Fleet, FleetRates, Vehicle
from riaride_simulator.messages import CommandType, InvalidCommandError

log = logging.getLogger(__name__)

Clock = Callable[[], datetime]
IdFactory = Callable[[], str]

# BR13: the platform gives up on an unlock after 30 s, so an older command is stale. The broker
# keeps the session while the simulator is down, so such commands can arrive after a restart.
DEFAULT_MAX_COMMAND_AGE = timedelta(seconds=30)


class Publisher(Protocol):
    def publish(self, topic: str, payload: dict, retain: bool = False) -> None: ...


class _RecentIds:
    """Remembers the last `capacity` message ids, to ignore QoS 1 duplicates."""

    def __init__(self, capacity: int = 1000) -> None:
        self._capacity = capacity
        self._ids: OrderedDict[str, None] = OrderedDict()

    def seen_before(self, message_id: str) -> bool:
        if message_id in self._ids:
            return True
        self._ids[message_id] = None
        if len(self._ids) > self._capacity:
            self._ids.popitem(last=False)
        return False


class Simulator:
    def __init__(
        self,
        fleet: Fleet,
        publisher: Publisher,
        clock: Clock,
        rng: random.Random,
        fault_probability: float = 0.0,
        rates: FleetRates | None = None,
        new_id: IdFactory = messages.new_message_id,
        max_command_age: timedelta = DEFAULT_MAX_COMMAND_AGE,
    ) -> None:
        if not 0.0 <= fault_probability <= 1.0:
            raise ValueError("fault_probability must be between 0 and 1")
        if max_command_age <= timedelta(0):
            raise ValueError("max_command_age must be positive")
        self._fleet = fleet
        self._publisher = publisher
        self._clock = clock
        self._rng = rng
        self._fault_probability = fault_probability
        self._rates = rates or FleetRates()
        self._new_id = new_id
        self._max_command_age = max_command_age
        self._recent_commands = _RecentIds()
        self._last_tick: datetime | None = None

    @property
    def fleet(self) -> Fleet:
        return self._fleet

    def start(self) -> None:
        """Announce the simulator and publish the initial state of every vehicle."""
        now = self._clock()
        self._publisher.publish(topics.STATUS_TOPIC, messages.status(True, now), retain=True)
        self._last_tick = now
        for vehicle in self._fleet.vehicles.values():
            self._publish_telemetry(vehicle, now)

    def tick(self) -> None:
        """Advance simulated time to now, raise random faults and publish telemetry."""
        now = self._clock()
        minutes = 0.0 if self._last_tick is None else (now - self._last_tick).total_seconds() / 60
        self._last_tick = now
        for vehicle in self._fleet.vehicles.values():
            vehicle.advance(minutes, self._rates, self._rng)
            if self._rng.random() < self._fault_probability:
                fault_code = self._rng.choice(sorted(messages.FAULT_DESCRIPTIONS))
                self.raise_fault(vehicle.vehicle_id, fault_code)
            self._publish_telemetry(vehicle, now)

    def raise_fault(self, vehicle_id: str, fault_code: str) -> None:
        """Publish one fault event for a vehicle, with a new message id."""
        if vehicle_id not in self._fleet.vehicles:
            raise KeyError(f"unknown vehicle: {vehicle_id}")
        event = messages.fault_event(fault_code, self._clock(), self._new_id())
        log.info("Fault %s on vehicle %s", fault_code, vehicle_id)
        self._publisher.publish(topics.events_topic(vehicle_id), event)

    def handle_command(self, topic: str, payload: bytes) -> None:
        """Apply an UNLOCK or LOCK command and answer with the vehicle's telemetry.

        Invalid, unknown, repeated and stale commands (older than the maximum age)
        are logged and ignored, so a bad message never stops the simulator.
        """
        vehicle_id = topics.vehicle_id_from_command_topic(topic)
        vehicle = self._fleet.vehicles.get(vehicle_id) if vehicle_id else None
        if vehicle is None:
            log.warning("Ignoring command for unknown vehicle on topic %s", topic)
            return
        try:
            command = messages.parse_command(json.loads(payload))
        except (json.JSONDecodeError, UnicodeDecodeError, InvalidCommandError) as error:
            log.warning("Ignoring invalid command for vehicle %s: %s", vehicle_id, error)
            return
        age = self._clock() - command.emitted_at
        if age > self._max_command_age:
            log.warning(
                "Ignoring stale command %s for vehicle %s, emitted %.0f s ago",
                command.message_id,
                vehicle_id,
                age.total_seconds(),
            )
            return
        if self._recent_commands.seen_before(command.message_id):
            log.info("Ignoring repeated command %s for vehicle %s", command.message_id, vehicle_id)
            return

        if command.command is CommandType.UNLOCK:
            vehicle.unlock()
        else:
            hub = self._fleet.hubs.get(command.hub_id) if command.hub_id else None
            if command.hub_id and hub is None:
                log.warning(
                    "LOCK for vehicle %s names unknown hub %s; locking in place",
                    vehicle_id,
                    command.hub_id,
                )
            vehicle.lock(hub)
        log.info("Vehicle %s: %s", vehicle_id, command.command.value)
        self._publish_telemetry(vehicle, self._clock())

    def _publish_telemetry(self, vehicle: Vehicle, now: datetime) -> None:
        message = messages.telemetry(vehicle, now, self._new_id())
        self._publisher.publish(topics.telemetry_topic(vehicle.vehicle_id), message)
