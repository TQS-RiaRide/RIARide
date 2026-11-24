package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;

// A vehicle at a hub as the guest sees it. batteryPct is null before the first telemetry reading.
public record HubVehicle(String code, VehicleType type, VehicleStatus status, Integer batteryPct, boolean bookable) {

	static HubVehicle of(Vehicle vehicle, boolean bookable) {
		var battery = vehicle.battery();
		return new HubVehicle(vehicle.getCode(), vehicle.getType(), vehicle.getStatus(),
			battery == null ? null : battery.percent(), bookable);
	}

}
