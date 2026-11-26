package com.riaride.fleet.internal.domain;

import java.util.EnumSet;
import java.util.Set;

// The vehicle lifecycle (architecture, section 10; Product Specification, section 3.3). The battery
// level is not a status: a bike below the booking minimum stays AVAILABLE and is only filtered out
// of booking (BR12).
public enum VehicleStatus {
  AVAILABLE,
  RESERVED,
  IN_USE,
  MAINTENANCE,
  RETIRED;

  // The transitions of the state diagram. A status never changes to itself, and a retired vehicle
  // never returns to service.
  public boolean canChangeTo(VehicleStatus target) {
    return next().contains(target);
  }

  private Set<VehicleStatus> next() {
    return switch (this) {
      case AVAILABLE -> EnumSet.of(RESERVED, MAINTENANCE, RETIRED);
      case RESERVED -> EnumSet.of(AVAILABLE, IN_USE, MAINTENANCE);
      case IN_USE -> EnumSet.of(AVAILABLE, MAINTENANCE);
      case MAINTENANCE -> EnumSet.of(AVAILABLE, RETIRED);
      case RETIRED -> EnumSet.noneOf(VehicleStatus.class);
    };
  }
}
