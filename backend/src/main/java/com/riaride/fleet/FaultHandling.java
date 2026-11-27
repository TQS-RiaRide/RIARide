package com.riaride.fleet;

// What the fleet did with a bike when a fault was reported on it (RIA-92; Product Specification,
// section 3.3).
public enum FaultHandling {
  // The bike was available and is now in maintenance.
  OUT_OF_SERVICE,
  // The bike was reserved and is now in maintenance; its reservation must be cancelled by the
  // trips module, which owns it.
  OUT_OF_SERVICE_RESERVATION_CANCELLED,
  // The bike is in use: it stays in use and moves to maintenance when its trip ends.
  PENDING_UNTIL_TRIP_ENDS,
  // The bike was already in maintenance and nothing changed.
  ALREADY_OUT_OF_SERVICE
}
