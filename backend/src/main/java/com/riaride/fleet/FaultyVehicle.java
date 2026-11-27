package com.riaride.fleet;

import java.util.UUID;

// A bike a fault was reported on, and what the fleet did with it (RIA-92).
public record FaultyVehicle(UUID vehicleId, String code, FaultHandling handling) {}
