package com.riaride.fleet;

// What the fleet did with a bike when a fault was reported on it (RIA-92; Product Specification,
// section 3.3).
public enum FaultHandling {
  // The bike was available and is now in maintenance.
  OUT_OF_SERVICE,
  // The bike was reserved and is now in maintenance. Its reservation belongs to the trips module,
  // which cancels it after the ticket commits (ReservationCancellationListener).
  OUT_OF_SERVICE_WAS_RESERVED,
  // The bike is in use: it stays in use and moves to maintenance when its trip ends.
  PENDING_UNTIL_TRIP_ENDS,
  // The bike was already in maintenance and nothing changed.
  ALREADY_OUT_OF_SERVICE
}
