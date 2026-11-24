package com.riaride.trips.internal.web;

import com.riaride.trips.internal.domain.Trip;
import java.time.Instant;
import java.util.UUID;

// status is STARTING until telemetry confirms the unlock, then IN_USE, or FAILED (RIA-54).
// startedAt is null until the trip is in use.
record TripResponse(
    UUID id, String status, String vehicleCode, Instant requestedAt, Instant startedAt) {

  static TripResponse from(Trip trip) {
    return new TripResponse(
        trip.getId(),
        trip.getStatus().name(),
        trip.getVehicleCode(),
        trip.getRequestedAt(),
        trip.getStartedAt());
  }
}
