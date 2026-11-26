package com.riaride.trips.internal.domain;

import java.util.EnumSet;
import java.util.Set;

// STARTING until telemetry confirms the unlock, then IN_USE; FAILED when the unlock is not
// confirmed in time (RIA-54). A trip in use is ENDED at a hub with a free slot (RIA-55).
public enum TripStatus {
  STARTING,
  IN_USE,
  FAILED,
  ENDED;

  // One vehicle at a time per pass: a pass has at most one trip in these statuses (BR6). Read-only,
  // so no caller can change the rule.
  public static final Set<TripStatus> ACTIVE = Set.copyOf(EnumSet.of(STARTING, IN_USE));
}
