package com.riaride.trips.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.VehicleReservations;
import com.riaride.passes.PassUsage;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Books a bike in advance (RIA-90, BR11): a guest with an active stay (BR1) and a usable pass
// reserves an available bike with enough battery (BR12), and it is held for the pass for 15
// minutes. Starting a trip on that bike within the hold uses the booking (StartTripService); once
// the hold ends unused, ReservationExpiryJob expires it and the bike is available again. The pass
// stays inactive until the first trip (BR4).
//
// A pass holds one bike at a time (BR6): a pass with a trip in progress or a bike still held cannot
// book another. A hold that already ended is expired here first, and its bike released, even if
// ReservationExpiryJob has not run yet. Two bookings of the same bike are settled by the vehicle
// row lock in fleet; two bookings for the same pass at once by the unique index
// uq_reservation_active_per_pass, whose violation is the same refusal as the check.
@Service
public class BookBikeService {

  private final GuestDirectory guests;
  private final PassUsage passes;
  private final VehicleReservations vehicles;
  private final ReservationRepository reservations;
  private final TripRepository trips;
  private final Clock clock;
  private final TripsProperties properties;

  public BookBikeService(
      GuestDirectory guests,
      PassUsage passes,
      VehicleReservations vehicles,
      ReservationRepository reservations,
      TripRepository trips,
      Clock clock,
      TripsProperties properties) {
    this.guests = guests;
    this.passes = passes;
    this.vehicles = vehicles;
    this.reservations = reservations;
    this.trips = trips;
    this.clock = clock;
    this.properties = properties;
  }

  @Transactional
  public Reservation book(BookBikeCommand command) {
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
    if (held.isPresent()) {
      throw new ReservationAlreadyHeldException();
    }
    var bike = vehicles.reserve(command.vehicleCode());
    try {
      return reservations.saveAndFlush(
          Reservation.hold(pass.passId(), bike, now, properties.reservationHold()));
    } catch (DataIntegrityViolationException concurrentBooking) {
      // Only uq_reservation_active_per_pass can refuse this insert: the bike's row is locked, so
      // another guest cannot reserve it meanwhile. The transaction rolls back, bike included.
      throw new ReservationAlreadyHeldException();
    }
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
