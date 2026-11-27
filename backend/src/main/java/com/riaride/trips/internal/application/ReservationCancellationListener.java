package com.riaride.trips.internal.application;

import com.riaride.maintenance.MaintenanceTicketOpened;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

// A ticket was opened for a bike (RIA-92): its active reservation, if any, is cancelled, so the
// guest's pass can hold another bike. It runs after the ticket's transaction commits, in a
// transaction of its own, so the reservation row is never locked after the vehicle row (trips
// locks the reservation first, see ReservationExpiryJob).
//
// If it fails, or has not run yet, nothing is retried: the bike is already in maintenance, so no
// trip can start on it, and the guest's next start cancels the hold (StartTripService checks
// through fleet that a held bike is still reserved). Otherwise ReservationExpiryJob ends the hold
// when it expires; Vehicle.release leaves a bike in maintenance as it is.
@Component
class ReservationCancellationListener {

  private final ReservationRepository reservations;

  ReservationCancellationListener(ReservationRepository reservations) {
    this.reservations = reservations;
  }

  @TransactionalEventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(MaintenanceTicketOpened ticket) {
    reservations
        .findByVehicleIdAndStatus(ticket.vehicleId(), ReservationStatus.ACTIVE)
        .ifPresent(Reservation::cancel);
  }
}
