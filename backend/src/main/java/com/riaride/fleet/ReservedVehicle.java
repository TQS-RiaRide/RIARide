package com.riaride.fleet;

import java.util.UUID;

// A bike reserved for a trip, with the hub it was docked at when it was reserved.
public record ReservedVehicle(UUID vehicleId, String code, UUID hubId) {}
