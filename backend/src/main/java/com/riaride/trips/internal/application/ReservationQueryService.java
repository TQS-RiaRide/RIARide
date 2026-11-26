package com.riaride.trips.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.passes.PassUsage;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.persistence.ReservationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads a guest's own booking, so the client can follow it from ACTIVE to USED or EXPIRED
// (RIA-90). A reservation belongs to a pass, so it is the guest's when it belongs to the guest's
// usable pass: a booking only exists while that pass can still take a trip.
@Service
public class ReservationQueryService {

  private final GuestDirectory guests;
  private final PassUsage passes;
  private final ReservationRepository reservations;

  public ReservationQueryService(
      GuestDirectory guests, PassUsage passes, ReservationRepository reservations) {
    this.guests = guests;
    this.passes = passes;
    this.reservations = reservations;
  }

  // Empty when the reservation does not exist or belongs to another pass.
  @Transactional(readOnly = true)
  public Optional<Reservation> findForGuest(String keycloakId, UUID reservationId) {
    return guests
        .findGuestId(keycloakId)
        .flatMap(passes::findUsablePass)
        .flatMap(pass ->
            reservations
                .findById(reservationId)
                .filter(reservation -> reservation.getPassId().equals(pass.passId())));
  }
}
