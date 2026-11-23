package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.RegisterVehicleCommand;
import com.riaride.fleet.internal.domain.VehicleType;
import java.util.UUID;

// The vehicle registration form (RIA-57). The rules on the code and the type are the domain's
// (Vehicle.register), so they are not repeated here.
record RegisterVehicleRequest(String code, VehicleType type) {

	RegisterVehicleCommand toCommand(UUID hubId) {
		return new RegisterVehicleCommand(hubId, code, type);
	}

}
