package com.riaride.trips.internal.web;

import com.riaride.trips.internal.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

// status is ACTIVE while the bike is held for the pass, USED once a trip on it starts and EXPIRED
// once the hold ended unused (RIA-90, BR11). hubId is where the bike was docked when it was booked.
record ReservationResponse(
    UUID id, String status, String vehicleCode, UUID hubId, Instant createdAt, Instant expiresAt) {

  static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        reservation.getStatus().name(),
        reservation.getVehicleCode(),
        reservation.getHubId(),
        reservation.getCreatedAt(),
        reservation.getExpiresAt());
  }
}
