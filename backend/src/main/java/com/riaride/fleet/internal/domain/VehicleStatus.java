package com.riaride.fleet.internal.domain;

// The vehicle lifecycle (architecture, section 10). The battery level is not a status: a bike
// below the booking minimum stays AVAILABLE and is only filtered out of booking (BR12).
public enum VehicleStatus {
  AVAILABLE,
  RESERVED,
  IN_USE,
  MAINTENANCE,
  RETIRED
}
