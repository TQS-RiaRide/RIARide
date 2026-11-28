package com.riaride.trips.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.persistence.TripRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads a guest's own trip, so the client can follow it from STARTING to IN_USE or FAILED (RIA-54),
// and lists their trips for the dashboard, active and past (RIA-107).
@Service
public class TripQueryService {

  private final GuestDirectory guests;
  private final TripRepository trips;

  public TripQueryService(GuestDirectory guests, TripRepository trips) {
    this.guests = guests;
    this.trips = trips;
  }

  // Empty when the trip does not exist or belongs to someone else.
  @Transactional(readOnly = true)
  public Optional<Trip> findForGuest(String keycloakId, UUID tripId) {
    return guests
        .findGuestId(keycloakId)
        .flatMap(guestId -> trips.findByIdAndGuestId(tripId, guestId));
  }

  // Newest first, at most 100; empty when the user is not a guest.
  @Transactional(readOnly = true)
  public List<Trip> listForGuest(String keycloakId) {
    return guests
        .findGuestId(keycloakId)
        .map(trips::findTop100ByGuestIdOrderByRequestedAtDesc)
        .orElse(List.of());
  }
}
