package com.riaride.trips.internal.application;

import com.riaride.fleet.VehicleReservations;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

// Ends the holds that were not used (RIA-54, BR11): once a reservation expires, its bike is
// available again, so a failed start (BR13) never keeps the bike or the pass stuck. Each
// reservation is expired in a transaction of its own, under its row lock, and checked again there:
// one used in the meantime, or whose trip is still starting, is left alone. Lock order: the
// reservation, then the vehicle.
@Component
public class ReservationExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

  private final ReservationRepository reservations;
  private final TripRepository trips;
  private final VehicleReservations vehicles;
  private final Clock clock;
  private final TransactionOperations transactions;

  ReservationExpiryJob(
      ReservationRepository reservations,
      TripRepository trips,
      VehicleReservations vehicles,
      Clock clock,
      TransactionOperations transactions) {
    this.reservations = reservations;
    this.trips = trips;
    this.vehicles = vehicles;
    this.clock = clock;
    this.transactions = transactions;
  }

  // Returns how many reservations it expired. The first run also waits one interval: without an
  // initial delay, Spring runs the job as soon as the context starts.
  @Scheduled(
      initialDelayString = "${riaride.trips.reservation-expiry-check-interval}",
      fixedDelayString = "${riaride.trips.reservation-expiry-check-interval}")
  public int expireOverdue() {
    var now = clock.instant();
    var expired = 0;
    for (var reservationId :
        reservations.findIdsByStatusAndExpiresAtLessThanEqual(ReservationStatus.ACTIVE, now)) {
      try {
        if (Boolean.TRUE.equals(transactions.execute(status -> expire(reservationId, now)))) {
          expired++;
        }
      } catch (RuntimeException notExpired) {
        log.error(
            "Overdue reservation {} not expired ({}); retrying on the next run",
            reservationId,
            notExpired.getClass().getSimpleName());
      }
    }
    return expired;
  }

  private boolean expire(UUID reservationId, Instant now) {
    var found = reservations.findByIdForUpdate(reservationId);
    if (found.isEmpty()) {
      return false;
    }
    var reservation = found.get();
    if (reservation.getStatus() != ReservationStatus.ACTIVE
        || !reservation.isExpiredAt(now)
        || trips.existsByReservationIdAndStatus(reservationId, TripStatus.STARTING)) {
      return false;
    }
    reservation.expire();
    vehicles.release(reservation.getVehicleId());
    return true;
  }
}
