package com.riaride.fleet;

import java.util.UUID;

// Public API of the fleet module for the trips module (RIA-54). All calls join the caller's
// transaction and lock the vehicle row, so two trips cannot take the same bike.
public interface VehicleReservations {

  // Reserves an available bike with enough battery (BR12) for a trip that is starting.
  // Throws VehicleNotFoundException for an unknown code and VehicleNotBookableException otherwise.
  ReservedVehicle reserve(String vehicleCode);

  // A held reservation is reused for a new start: its bike must still be reserved (RIA-92).
  // Throws VehicleNotBookableException otherwise, for example once a ticket took it out of service.
  void confirmReserved(UUID vehicleId);

  // Telemetry confirmed the unlock: the reserved bike is in use and leaves its hub. Returns false,
  // changing nothing, when the bike is no longer reserved (for example, taken out of service).
  boolean startRide(UUID vehicleId);

  // The reservation ended without a ride (BR11): a bike still reserved is available again.
  void release(UUID vehicleId);
}
