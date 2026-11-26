package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.accounts.ActiveGuest;
import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleNotBookableException;
import com.riaride.fleet.VehicleReservations;
import com.riaride.passes.PassUsage;
import com.riaride.passes.UsablePass;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): booking a bike in advance (RIA-90, BR11). A guest with an active stay and a
// usable pass reserves an available bike, held for 15 minutes; the pass is not activated (BR4). A
// pass with a trip in progress or a booking still held cannot book another bike (BR6), and a hold
// that already ended is expired first, with its bike released. Concurrent bookings are checked in
// BookBikeApiIT against PostgreSQL.
@ExtendWith(MockitoExtension.class)
class BookBikeServiceTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final Duration HOLD = Duration.ofMinutes(15);
  private static final String KEYCLOAK_ID = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
  private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000c0001");
  private static final UUID STAY = UUID.fromString("00000000-0000-0000-0000-0000000d0001");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final ReservedVehicle EB_701 =
      new ReservedVehicle(UUID.fromString("00000000-0000-0000-0000-0000000e0701"), "EB-701", HUB);
  private static final ReservedVehicle EB_702 =
      new ReservedVehicle(UUID.fromString("00000000-0000-0000-0000-0000000e0702"), "EB-702", HUB);

  @Mock private GuestDirectory guests;

  @Mock private PassUsage passes;

  @Mock private VehicleReservations vehicles;

  @Mock private ReservationRepository reservations;

  @Mock private TripRepository trips;

  private BookBikeService service;

  @BeforeEach
  void setUp() {
    service =
        new BookBikeService(
            guests,
            passes,
            vehicles,
            reservations,
            trips,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new TripsProperties(Duration.ofSeconds(30), HOLD));
  }

  private void givenAGuestWithAUsablePass() {
    when(guests.findActiveGuest(KEYCLOAK_ID))
        .thenReturn(Optional.of(new ActiveGuest(GUEST, STAY, LocalDate.of(2026, 12, 2))));
    when(passes.findUsablePass(GUEST)).thenReturn(Optional.of(new UsablePass(PASS)));
  }

  private void givenNoTripInProgressAndNoBooking() {
    givenAGuestWithAUsablePass();
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(false);
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.empty());
  }

  private Reservation book(String code) {
    return service.book(new BookBikeCommand(KEYCLOAK_ID, code));
  }

  @Requirement("RIA-90")
  @Test
  void shouldReserveTheBikeAndHoldItFor15Minutes() {
    givenNoTripInProgressAndNoBooking();
    when(vehicles.reserve("EB-701")).thenReturn(EB_701);
    when(reservations.saveAndFlush(any(Reservation.class))).then(returnsFirstArg());

    var booking = book("EB-701");

    assertThat(booking.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    assertThat(booking.getPassId()).isEqualTo(PASS);
    assertThat(booking.getVehicleId()).isEqualTo(EB_701.vehicleId());
    assertThat(booking.getVehicleCode()).isEqualTo("EB-701");
    assertThat(booking.getHubId()).isEqualTo(HUB);
    assertThat(booking.getCreatedAt()).isEqualTo(NOW);
    assertThat(booking.getExpiresAt()).isEqualTo(NOW.plus(HOLD));
    verify(passes, never()).activateOnFirstTrip(any(), any());
  }

  @Requirement("RIA-90")
  @Test
  void shouldRefuseAUserWithoutAnActiveStay() {
    when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> book("EB-701")).isInstanceOf(NoActiveStayException.class);
    verifyNoInteractions(vehicles, reservations);
  }

  @Requirement("RIA-90")
  @Test
  void shouldRefuseAGuestWithoutAUsablePass() {
    when(guests.findActiveGuest(KEYCLOAK_ID))
        .thenReturn(Optional.of(new ActiveGuest(GUEST, STAY, LocalDate.of(2026, 12, 2))));
    when(passes.findUsablePass(GUEST)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> book("EB-701")).isInstanceOf(NoUsablePassException.class);
    verifyNoInteractions(vehicles, reservations);
  }

  // BR6: the bike of the trip in progress is the pass's one bike.
  @Requirement("RIA-90")
  @Test
  void shouldRefuseAPassWithATripInProgress() {
    givenAGuestWithAUsablePass();
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(true);

    assertThatThrownBy(() -> book("EB-701")).isInstanceOf(TripAlreadyInProgressException.class);
    verifyNoInteractions(vehicles, reservations);
  }

  // BR6 and BR11: while a booking is held, the pass cannot book any bike, the same one included.
  @Requirement("RIA-90")
  @Test
  void shouldRefuseASecondBookingWhileOneIsHeld() {
    givenAGuestWithAUsablePass();
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(false);
    var held = Reservation.hold(PASS, EB_701, NOW.minus(Duration.ofMinutes(14)), HOLD);
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(held));

    assertThatThrownBy(() -> book("EB-702")).isInstanceOf(ReservationAlreadyHeldException.class);
    assertThat(held.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    verifyNoInteractions(vehicles);
  }

  // BR11: at 15 minutes the hold is over, even if ReservationExpiryJob has not run yet.
  @Requirement("RIA-90")
  @Test
  void shouldExpireAHoldThatEndedAndBookTheNewBike() {
    givenAGuestWithAUsablePass();
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(false);
    var ended = Reservation.hold(PASS, EB_701, NOW.minus(HOLD), HOLD);
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(ended));
    when(reservations.saveAndFlush(any(Reservation.class))).then(returnsFirstArg());
    when(vehicles.reserve("EB-702")).thenReturn(EB_702);

    var booking = book("EB-702");

    assertThat(ended.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    verify(vehicles).release(EB_701.vehicleId());
    assertThat(booking.getVehicleCode()).isEqualTo("EB-702");
    assertThat(booking.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
  }

  @Requirement("RIA-90")
  @Test
  void shouldLetTheFleetRefuseABikeThatCannotBeBooked() {
    givenNoTripInProgressAndNoBooking();
    when(vehicles.reserve("EB-701")).thenThrow(new VehicleNotBookableException());

    assertThatThrownBy(() -> book("EB-701")).isInstanceOf(VehicleNotBookableException.class);
    verify(reservations, never()).saveAndFlush(any());
  }

  // Two bookings for the same pass at once: the second insert breaks
  // uq_reservation_active_per_pass, and gets the same answer as the check.
  @Requirement("RIA-90")
  @Test
  void shouldAnswerAConcurrentBookingForTheSamePassAsAlreadyHeld() {
    givenNoTripInProgressAndNoBooking();
    when(vehicles.reserve("EB-701")).thenReturn(EB_701);
    when(reservations.saveAndFlush(any(Reservation.class)))
        .thenThrow(new DataIntegrityViolationException("uq_reservation_active_per_pass"));

    assertThatThrownBy(() -> book("EB-701")).isInstanceOf(ReservationAlreadyHeldException.class);
  }
}
