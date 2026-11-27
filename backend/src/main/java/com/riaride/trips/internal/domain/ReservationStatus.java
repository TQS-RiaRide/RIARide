package com.riaride.trips.internal.domain;

// ACTIVE while the bike is held for the pass (BR11), USED once a trip on it starts, EXPIRED once
// the hold ended unused, CANCELLED when the bike left service because of a fault (RIA-92).
public enum ReservationStatus {
  ACTIVE,
  USED,
  EXPIRED,
  CANCELLED
}
