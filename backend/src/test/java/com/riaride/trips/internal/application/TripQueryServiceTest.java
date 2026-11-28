package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): a guest reads only their own trips, one by id (RIA-54) or all of them,
// newest first, for the dashboard (RIA-107). A user who is not a guest has none.
@ExtendWith(MockitoExtension.class)
class TripQueryServiceTest {

  private static final String KEYCLOAK_ID = "8c1f6a52-4d3e-4b9a-9f0e-1a2b3c4d5e6f";
  private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000c0001");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");

  @Mock private GuestDirectory guests;

  @Mock private TripRepository trips;

  @InjectMocks private TripQueryService service;

  private static Trip trip() {
    var reservation =
        Reservation.hold(
            PASS,
            new ReservedVehicle(UUID.randomUUID(), "EB-301", UUID.randomUUID()),
            NOW,
            Duration.ofMinutes(15));
    return Trip.requestStart(reservation, GUEST, NOW);
  }

  @Requirement("RIA-107")
  @Test
  void shouldListTheGuestsTripsNewestFirst() {
    var trips = List.of(trip(), trip());
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(this.trips.findTop100ByGuestIdOrderByRequestedAtDesc(GUEST)).thenReturn(trips);

    assertThat(service.listForGuest(KEYCLOAK_ID)).isEqualTo(trips);
  }

  @Requirement("RIA-107")
  @Test
  void shouldListNoTripForAUserWhoIsNotAGuest() {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.empty());

    assertThat(service.listForGuest(KEYCLOAK_ID)).isEmpty();
    verifyNoInteractions(trips);
  }

  @Requirement("RIA-54")
  @Test
  void shouldReadATripOnlyAmongTheGuestsOwn() {
    var trip = trip();
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(trips.findByIdAndGuestId(trip.getId(), GUEST)).thenReturn(Optional.of(trip));

    assertThat(service.findForGuest(KEYCLOAK_ID, trip.getId())).containsSame(trip);
  }
}
