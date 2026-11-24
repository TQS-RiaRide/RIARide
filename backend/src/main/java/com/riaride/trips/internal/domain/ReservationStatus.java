package com.riaride.trips.internal.domain;

// ACTIVE while the bike is held for the pass (BR11), USED once a trip on it starts. Story 3.3 adds
// EXPIRED and CANCELLED.
public enum ReservationStatus {
  ACTIVE,
  USED
}
