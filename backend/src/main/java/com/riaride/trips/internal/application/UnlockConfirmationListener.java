package com.riaride.trips.internal.application;

import com.riaride.fleet.VehicleReservations;
import com.riaride.passes.PassUsage;
import com.riaride.telemetry.VehicleUnlocked;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Confirms a starting trip when telemetry reports its bike unlocked (RIA-54). Runs in the telemetry
// message's transaction, with the bike's latest trip locked, so it never races the timeout job. In
// time (before 30 s), the trip is in use, the bike leaves its hub, the first trip activates the
// pass (BR4) and the reservation is used. Too late, or for a trip that already failed, the bike is
// locked again. If the bike is no longer reserved, or the pass can no longer take a trip, the trip
// fails and the bike is locked: these are outcomes, not exceptions, so the battery reading in the
// same transaction is still stored. Repeated and stale readings change nothing.
@Component
class UnlockConfirmationListener {

  private final TripRepository trips;
  private final ReservationRepository reservations;
  private final VehicleReservations vehicles;
  private final PassUsage passes;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final TripsProperties properties;

  UnlockConfirmationListener(
      TripRepository trips,
      ReservationRepository reservations,
      VehicleReservations vehicles,
      PassUsage passes,
      ApplicationEventPublisher events,
      Clock clock,
      TripsProperties properties) {
    this.trips = trips;
    this.reservations = reservations;
    this.vehicles = vehicles;
    this.passes = passes;
    this.events = events;
    this.clock = clock;
    this.properties = properties;
  }

  @EventListener
  @Transactional
  void on(VehicleUnlocked unlocked) {
    trips
        .findFirstByVehicleCodeOrderByRequestedAtDesc(unlocked.vehicleCode())
        .ifPresent(trip -> {
          var now = clock.instant();
          switch (trip.confirmUnlock(unlocked.emittedAt(), now, properties.unlockTimeout())) {
            case STARTED -> start(trip, now);
            case TOO_LATE ->
                events.publishEvent(new TripFailed(trip.getVehicleCode(), trip.getHubId()));
            case ALREADY_STARTED, ALREADY_ENDED, STALE -> {
              // Nothing to do: the simulator repeats UNLOCKED while the bike is ridden, a reading
              // sent during the ride may arrive after it ended (the end already sent LOCK), and a
              // redelivered reading from before the request is not this trip's answer.
            }
          }
        });
  }

  // The pass is checked (and locked) before the bike changes, so a refusal changes nothing else.
  private void start(Trip trip, Instant now) {
    var reservation =
        reservations
            .findById(trip.getReservationId())
            .filter(held -> held.getStatus() == ReservationStatus.ACTIVE);
    if (reservation.isEmpty()
        || !passes.canStartTrip(trip.getPassId(), now)
        || !vehicles.startRide(trip.getVehicleId())) {
      trip.failStart(now);
      events.publishEvent(new TripFailed(trip.getVehicleCode(), trip.getHubId()));
      return;
    }
    passes.activateOnFirstTrip(trip.getPassId(), now);
    reservation.get().markUsed();
  }
}
