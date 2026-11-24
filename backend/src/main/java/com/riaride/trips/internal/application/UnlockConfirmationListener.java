package com.riaride.trips.internal.application;

import com.riaride.fleet.VehicleReservations;
import com.riaride.passes.PassUsage;
import com.riaride.telemetry.VehicleUnlocked;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Confirms a starting trip when telemetry reports its bike unlocked (RIA-54). Runs in the
// telemetry message's transaction, with the bike's latest trip locked, so it never races the
// timeout job. In time (before 30 s), the trip is in use, the bike leaves its hub, the first trip
// activates the pass (BR4) and the reservation is used. Too late, or for a trip that already
// failed,
// the bike is locked again. Repeated and stale readings change nothing.
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
            case STARTED -> {
              vehicles.startRide(trip.getVehicleId());
              passes.activateOnFirstTrip(trip.getPassId(), now);
              reservations
                  .findById(trip.getReservationId())
                  .orElseThrow(
                      () -> new IllegalStateException("A trip's reservation no longer exists"))
                  .markUsed();
            }
            case TOO_LATE ->
                events.publishEvent(new TripFailed(trip.getVehicleCode(), trip.getHubId()));
            case ALREADY_STARTED, STALE -> {
              // Nothing to do: the simulator repeats UNLOCKED while the bike is ridden, and a
              // redelivered reading from before the request is not this trip's answer.
            }
          }
        });
  }
}
