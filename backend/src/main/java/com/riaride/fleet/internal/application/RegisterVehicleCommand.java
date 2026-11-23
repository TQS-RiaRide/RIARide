package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.VehicleType;
import java.util.UUID;

// An operator registers a vehicle with its code and type at a hub (RIA-57).
public record RegisterVehicleCommand(UUID hubId, String code, VehicleType type) {
}
