package com.riaride.fleet;

import java.util.UUID;

// Public API of the fleet module for the trips module (RIA-54). All calls join the caller's
// transaction and lock the vehicle row, so two trips cannot take the same bike.
public interface VehicleReservations {

  // Reserves an available bike with enough battery (BR12) for a trip that is starting.
  // Throws VehicleNotFoundException for an unknown code and VehicleNotBookableException otherwise.
  ReservedVehicle reserve(String vehicleCode);

  // Whether a held reservation's bike is still reserved (RIA-92). False for an unknown bike or one
  // that left RESERVED, for example once a ticket took it out of service.
  boolean isReserved(UUID vehicleId);

  // Telemetry confirmed the unlock: the reserved bike is in use and leaves its hub. Returns false,
  // changing nothing, when the bike is no longer reserved (for example, taken out of service).
  boolean startRide(UUID vehicleId);

  // The reservation ended without a ride (BR11): a bike still reserved is available again.
  void release(UUID vehicleId);
}
