package com.riaride.trips.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.VehicleReservations;
import com.riaride.passes.PassUsage;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Starts a trip now (RIA-54): a guest with an active stay (BR1) and a usable pass reserves the bike
// (BR11, BR12) and gets a STARTING trip; UNLOCK is sent once this transaction commits. The checks
// here give the common refusals a clear reason. Concurrent starts are settled by the database: the
// vehicle row lock lets one guest reserve a bike, and the unique indexes allow one active
// reservation and one trip in progress per pass (BR6).
//
// A failed start leaves its reservation active, so the bike stays held for the pass: a retry on the
// same bike reuses it, and another bike is refused until it expires (BR11). An expired hold is
// ended here, and its bike released, even if ReservationExpiryJob has not run yet.
@Service
public class StartTripService {

  private final GuestDirectory guests;
  private final PassUsage passes;
  private final VehicleReservations vehicles;
  private final ReservationRepository reservations;
  private final TripRepository trips;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final TripsProperties properties;

  public StartTripService(
      GuestDirectory guests,
      PassUsage passes,
      VehicleReservations vehicles,
      ReservationRepository reservations,
      TripRepository trips,
      ApplicationEventPublisher events,
      Clock clock,
      TripsProperties properties) {
    this.guests = guests;
    this.passes = passes;
    this.vehicles = vehicles;
    this.reservations = reservations;
    this.trips = trips;
    this.events = events;
    this.clock = clock;
    this.properties = properties;
  }

  @Transactional
  public Trip start(StartTripCommand command) {
    var guest =
        guests.findActiveGuest(command.keycloakId()).orElseThrow(NoActiveStayException::new);
    var pass = passes.findUsablePass(guest.guestId()).orElseThrow(NoUsablePassException::new);
    if (trips.existsByPassIdAndStatusIn(pass.passId(), TripStatus.ACTIVE)) {
      throw new TripAlreadyInProgressException();
    }
    var now = clock.instant();
    var held =
        reservations
            .findByPassIdAndStatus(pass.passId(), ReservationStatus.ACTIVE)
            .filter(reservation -> isStillHeld(reservation, now));
    Reservation reservation;
    if (held.isPresent()) {
      if (!held.get().isFor(command.vehicleCode())) {
        throw new ReservationHeldForAnotherBikeException();
      }
      reservation = held.get();
      // A ticket may have taken the bike out of service before its reservation was cancelled.
      vehicles.confirmReserved(reservation.getVehicleId());
    } else {
      var bike = vehicles.reserve(command.vehicleCode());
      reservation =
          reservations.saveAndFlush(
              Reservation.hold(pass.passId(), bike, now, properties.reservationHold()));
    }
    var trip = trips.saveAndFlush(Trip.requestStart(reservation, guest.guestId(), now));
    events.publishEvent(new TripStartRequested(trip.getVehicleCode()));
    return trip;
  }

  // An expired hold ends before a new bike is reserved: flushed first, so the pass's partial unique
  // index (uq_reservation_active_per_pass) is free for the new reservation.
  private boolean isStillHeld(Reservation reservation, Instant now) {
    if (!reservation.isExpiredAt(now)) {
      return true;
    }
    reservation.expire();
    reservations.saveAndFlush(reservation);
    vehicles.release(reservation.getVehicleId());
    return false;
  }
}
