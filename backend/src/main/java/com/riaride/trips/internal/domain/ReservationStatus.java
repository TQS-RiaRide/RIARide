package com.riaride.trips.internal.domain;

// ACTIVE while the bike is held for the pass (BR11), USED once a trip on it starts, EXPIRED once
// the
// hold ended unused. Story 3.3 adds CANCELLED.
public enum ReservationStatus {
  ACTIVE,
  USED,
  EXPIRED
}
