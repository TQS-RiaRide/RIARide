package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.passes.PassUsage;
import com.riaride.passes.UsablePass;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.persistence.ReservationRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): a guest reads only the bookings of their own usable pass (RIA-90).
@ExtendWith(MockitoExtension.class)
class ReservationQueryServiceTest {

  private static final String KEYCLOAK_ID = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
  private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000c0001");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID OTHER_PASS = UUID.fromString("1db8bea0-1e3a-491a-91c6-a9cfe94a8cfb");
  private static final ReservedVehicle EB_701 =
      new ReservedVehicle(
          UUID.fromString("00000000-0000-0000-0000-0000000e0701"),
          "EB-701",
          UUID.fromString("00000000-0000-0000-0000-00000000a001"));

  @Mock private GuestDirectory guests;

  @Mock private PassUsage passes;

  @Mock private ReservationRepository reservations;

  private ReservationQueryService service;

  @BeforeEach
  void setUp() {
    service = new ReservationQueryService(guests, passes, reservations);
  }

  private static Reservation bookingOf(UUID passId) {
    return Reservation.hold(passId, EB_701, FixedClockConfiguration.NOW, Duration.ofMinutes(15));
  }

  @Requirement("RIA-90")
  @Test
  void shouldFindABookingOfTheGuestsPass() {
    var booking = bookingOf(PASS);
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(passes.findUsablePass(GUEST)).thenReturn(Optional.of(new UsablePass(PASS)));
    when(reservations.findById(booking.getId())).thenReturn(Optional.of(booking));

    assertThat(service.findForGuest(KEYCLOAK_ID, booking.getId())).containsSame(booking);
  }

  @Requirement("RIA-90")
  @Test
  void shouldNotFindAnotherPassesBooking() {
    var booking = bookingOf(OTHER_PASS);
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(passes.findUsablePass(GUEST)).thenReturn(Optional.of(new UsablePass(PASS)));
    when(reservations.findById(booking.getId())).thenReturn(Optional.of(booking));

    assertThat(service.findForGuest(KEYCLOAK_ID, booking.getId())).isEmpty();
  }

  @Requirement("RIA-90")
  @Test
  void shouldNotFindABookingForAUserWhoIsNotAGuest() {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.empty());

    assertThat(service.findForGuest(KEYCLOAK_ID, UUID.randomUUID())).isEmpty();
    verifyNoInteractions(passes, reservations);
  }
}
