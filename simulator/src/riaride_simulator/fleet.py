"""Fleet model: hubs, vehicles and how their state changes over time."""

from __future__ import annotations

import json
import math
import random
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path

# Roughly 111 km per degree of latitude; good enough for a simulated ride.
_METRES_PER_DEGREE = 111_000.0


class LockState(StrEnum):
    LOCKED = "LOCKED"
    UNLOCKED = "UNLOCKED"


@dataclass(frozen=True)
class Position:
    lat: float
    lon: float

    def __post_init__(self) -> None:
        if not -90 <= self.lat <= 90:
            raise ValueError(f"latitude out of range: {self.lat}")
        if not -180 <= self.lon <= 180:
            raise ValueError(f"longitude out of range: {self.lon}")


@dataclass(frozen=True)
class Hub:
    hub_id: str
    name: str
    position: Position


@dataclass(frozen=True)
class FleetRates:
    """How fast batteries change and bikes move, per minute of simulated time."""

    drain_pct_per_min: float = 0.4
    charge_pct_per_min: float = 0.5
    speed_m_per_min: float = 250.0


@dataclass
class Vehicle:
    vehicle_id: str
    battery_pct: float
    lock_state: LockState
    hub_id: str | None
    position: Position

    @property
    def battery_level(self) -> int:
        """Battery as the whole percentage that is published (0 to 100)."""
        return max(0, min(100, math.floor(self.battery_pct)))

    def advance(self, minutes: float, rates: FleetRates, rng: random.Random) -> None:
        """Advance the vehicle by `minutes` of simulated time.

        A locked vehicle at a hub charges; an unlocked vehicle drains its
        battery and moves in a random direction. A locked vehicle outside a
        hub stays as it is.
        """
        if minutes <= 0:
            return
        if self.lock_state is LockState.UNLOCKED:
            self.battery_pct = max(0.0, self.battery_pct - rates.drain_pct_per_min * minutes)
            self.position = _move(self.position, rates.speed_m_per_min * minutes, rng)
        elif self.hub_id is not None:
            self.battery_pct = min(100.0, self.battery_pct + rates.charge_pct_per_min * minutes)

    def unlock(self) -> None:
        """Start a ride: the vehicle leaves its hub and keeps its position."""
        self.lock_state = LockState.UNLOCKED
        self.hub_id = None

    def lock(self, hub: Hub | None) -> None:
        """End a ride, docked at `hub` if given, otherwise locked where it is."""
        self.lock_state = LockState.LOCKED
        if hub is not None:
            self.hub_id = hub.hub_id
            self.position = hub.position


@dataclass
class Fleet:
    hubs: dict[str, Hub]
    vehicles: dict[str, Vehicle]

    @classmethod
    def from_file(cls, path: Path) -> Fleet:
        return cls.from_dict(json.loads(path.read_text(encoding="utf-8")))

    @classmethod
    def from_dict(cls, data: dict) -> Fleet:
        hubs: dict[str, Hub] = {}
        for raw in data["hubs"]:
            hub = Hub(raw["id"], raw["name"], Position(raw["lat"], raw["lon"]))
            if hub.hub_id in hubs:
                raise ValueError(f"duplicate hub id: {hub.hub_id}")
            hubs[hub.hub_id] = hub

        vehicles: dict[str, Vehicle] = {}
        for raw in data["vehicles"]:
            vehicle_id = raw["id"]
            if vehicle_id in vehicles:
                raise ValueError(f"duplicate vehicle id: {vehicle_id}")
            battery = float(raw["batteryPct"])
            if not 0 <= battery <= 100:
                raise ValueError(f"battery out of range for {vehicle_id}: {battery}")
            hub = hubs.get(raw["hubId"])
            if hub is None:
                raise ValueError(f"vehicle {vehicle_id} starts at unknown hub {raw['hubId']}")
            vehicles[vehicle_id] = Vehicle(
                vehicle_id=vehicle_id,
                battery_pct=battery,
                lock_state=LockState.LOCKED,
                hub_id=hub.hub_id,
                position=hub.position,
            )
        if not vehicles:
            raise ValueError("the fleet has no vehicles")
        return cls(hubs, vehicles)


def _move(start: Position, metres: float, rng: random.Random) -> Position:
    bearing = rng.uniform(0, 2 * math.pi)
    d_lat = metres * math.cos(bearing) / _METRES_PER_DEGREE
    cos_lat = max(math.cos(math.radians(start.lat)), 1e-6)
    d_lon = metres * math.sin(bearing) / (_METRES_PER_DEGREE * cos_lat)
    lat = max(-90.0, min(90.0, start.lat + d_lat))
    lon = (start.lon + d_lon + 180.0) % 360.0 - 180.0
    return Position(round(lat, 6), round(lon, 6))
