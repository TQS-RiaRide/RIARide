package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import java.util.UUID;

// A vehicle just registered. batteryPct is null until the first telemetry reading arrives.
record VehicleResponse(UUID id, String code, VehicleType type, VehicleStatus status, UUID hubId,
	Integer batteryPct) {

	static VehicleResponse from(Vehicle vehicle) {
		var battery = vehicle.battery();
		return new VehicleResponse(vehicle.getId(), vehicle.getCode(), vehicle.getType(), vehicle.getStatus(),
			vehicle.getHubId(), battery == null ? null : battery.percent());
	}

}
