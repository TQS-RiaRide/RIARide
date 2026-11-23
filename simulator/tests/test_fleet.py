import random

import pytest

from riaride_simulator.fleet import Fleet, FleetRates, LockState, Position
from tests.conftest import HUB_PRAIA, HUB_SALINAS, fleet_data

RATES = FleetRates(drain_pct_per_min=0.4, charge_pct_per_min=0.5, speed_m_per_min=250)


def test_vehicles_start_locked_at_their_hub(fleet):
    vehicle = fleet.vehicles["EB-101"]

    assert vehicle.lock_state is LockState.LOCKED
    assert vehicle.hub_id == HUB_PRAIA
    assert vehicle.position == fleet.hubs[HUB_PRAIA].position


def test_unlocked_vehicle_drains_battery_and_moves(fleet):
    vehicle = fleet.vehicles["EB-101"]
    vehicle.unlock()
    start = vehicle.position

    vehicle.advance(10, RATES, random.Random(1))

    assert vehicle.battery_pct == pytest.approx(76.0)
    assert vehicle.position != start
    assert vehicle.hub_id is None


def test_locked_vehicle_at_hub_charges_up_to_100(fleet):
    vehicle = fleet.vehicles["EB-101"]

    vehicle.advance(100, RATES, random.Random(1))

    assert vehicle.battery_pct == 100.0


def test_locked_vehicle_outside_a_hub_keeps_its_battery(fleet):
    vehicle = fleet.vehicles["EB-101"]
    vehicle.unlock()
    vehicle.lock(None)

    vehicle.advance(30, RATES, random.Random(1))

    assert vehicle.battery_pct == 80.0
    assert vehicle.hub_id is None


def test_battery_never_drops_below_zero(fleet):
    vehicle = fleet.vehicles["EB-102"]
    vehicle.unlock()

    vehicle.advance(1000, RATES, random.Random(1))

    assert vehicle.battery_pct == 0.0
    assert vehicle.battery_level == 0


def test_no_time_passed_changes_nothing(fleet):
    vehicle = fleet.vehicles["EB-101"]
    vehicle.unlock()
    start = vehicle.position

    vehicle.advance(0, RATES, random.Random(1))

    assert vehicle.battery_pct == 80.0
    assert vehicle.position == start


def test_published_battery_level_is_rounded_down(fleet):
    vehicle = fleet.vehicles["EB-101"]
    vehicle.battery_pct = 19.9

    assert vehicle.battery_level == 19


def test_locking_at_a_hub_docks_the_vehicle_there(fleet):
    vehicle = fleet.vehicles["EB-101"]
    vehicle.unlock()

    vehicle.lock(fleet.hubs[HUB_SALINAS])

    assert vehicle.lock_state is LockState.LOCKED
    assert vehicle.hub_id == HUB_SALINAS
    assert vehicle.position == fleet.hubs[HUB_SALINAS].position


@pytest.mark.parametrize(
    ("change", "message"),
    [
        (lambda d: d["vehicles"].append(dict(d["vehicles"][0])), "duplicate vehicle id"),
        (lambda d: d["hubs"].append(dict(d["hubs"][0])), "duplicate hub id"),
        (lambda d: d["vehicles"][0].update(hubId="nowhere"), "unknown hub"),
        (lambda d: d["vehicles"][0].update(batteryPct=101), "battery out of range"),
        (lambda d: d.update(vehicles=[]), "no vehicles"),
    ],
)
def test_invalid_fleet_files_are_rejected(change, message):
    data = fleet_data()
    change(data)

    with pytest.raises(ValueError, match=message):
        Fleet.from_dict(data)


def test_fleet_loads_from_a_file(tmp_path):
    path = tmp_path / "fleet.json"
    path.write_text(
        '{"hubs": [{"id": "h1", "name": "Hub", "lat": 40.6, "lon": -8.6}],'
        ' "vehicles": [{"id": "EB-1", "hubId": "h1", "batteryPct": 50}]}',
        encoding="utf-8",
    )

    fleet = Fleet.from_file(path)

    assert list(fleet.vehicles) == ["EB-1"]


@pytest.mark.parametrize(("lat", "lon"), [(91, 0), (-91, 0), (0, 181), (0, -181)])
def test_positions_out_of_range_are_rejected(lat, lon):
    with pytest.raises(ValueError):
        Position(lat, lon)


def test_default_fleet_file_is_valid():
    from riaride_simulator.config import DEFAULT_FLEET_FILE

    fleet = Fleet.from_file(DEFAULT_FLEET_FILE)

    assert len(fleet.vehicles) >= 10
    assert any(v.battery_level < 20 for v in fleet.vehicles.values())
