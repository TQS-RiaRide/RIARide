package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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
import com.riaride.trips.internal.domain.Trip;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

// Unit test (Surefire): starting a trip now (RIA-54). Only a guest with an active stay (BR1) and a
// usable pass can start one, one at a time per pass (BR6). The bike is reserved and held for 15
// minutes (BR11), the trip is STARTING, and the unlock is sent once the transaction commits. After
// a failed start the reservation is still held: a retry on the same bike reuses it, and another
// bike is refused until it expires. An expired hold is ended here and its bike released, even
// before ReservationExpiryJob runs. The concurrent cases are in StartTripApiIT.
@ExtendWith(MockitoExtension.class)
class StartTripServiceTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final String KEYCLOAK_ID = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0201");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final ReservedVehicle EB_201 = new ReservedVehicle(VEHICLE, "EB-201", HUB);
  private static final TripsProperties PROPERTIES =
      new TripsProperties(Duration.ofSeconds(30), Duration.ofMinutes(15));

  @Mock private GuestDirectory guests;

  @Mock private PassUsage passes;

  @Mock private VehicleReservations vehicles;

  @Mock private ReservationRepository reservations;

  @Mock private TripRepository trips;

  @Mock private ApplicationEventPublisher events;

  private StartTripService service;

  @BeforeEach
  void setUp() {
    service =
        new StartTripService(
            guests,
            passes,
            vehicles,
            reservations,
            trips,
            events,
            Clock.fixed(NOW, ZoneOffset.UTC),
            PROPERTIES);
    lenient()
        .when(guests.findActiveGuest(KEYCLOAK_ID))
        .thenReturn(
            Optional.of(new ActiveGuest(GUEST, UUID.randomUUID(), LocalDate.parse("2026-12-02"))));
    lenient().when(passes.findUsablePass(GUEST)).thenReturn(Optional.of(new UsablePass(PASS)));
    lenient()
        .when(reservations.saveAndFlush(any(Reservation.class)))
        .thenAnswer(call -> call.getArgument(0));
    lenient().when(trips.saveAndFlush(any(Trip.class))).thenAnswer(call -> call.getArgument(0));
  }

  private static StartTripCommand start(String vehicleCode) {
    return new StartTripCommand(KEYCLOAK_ID, vehicleCode);
  }

  @Requirement("RIA-54")
  @Test
  void shouldReserveBikeAndCreateStartingTrip() {
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(vehicles.reserve("EB-201")).thenReturn(EB_201);

    var trip = service.start(start("EB-201"));

    var held = ArgumentCaptor.forClass(Reservation.class);
    verify(reservations).saveAndFlush(held.capture());
    assertThat(held.getValue().getPassId()).isEqualTo(PASS);
    assertThat(held.getValue().getVehicleId()).isEqualTo(VEHICLE);
    assertThat(held.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
    assertThat(trip.getGuestId()).isEqualTo(GUEST);
    assertThat(trip.getReservationId()).isEqualTo(held.getValue().getId());
    assertThat(trip.getRequestedAt()).isEqualTo(NOW);
    verify(trips).saveAndFlush(trip);
    verify(events).publishEvent(new TripStartRequested("EB-201"));
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseWhenTripAlreadyInProgress() {
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(true);

    assertThatThrownBy(() -> service.start(start("EB-202")))
        .isInstanceOf(TripAlreadyInProgressException.class);

    verify(vehicles, never()).reserve(anyString());
    verify(trips, never()).saveAndFlush(any());
    verifyNoInteractions(events);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseWithoutUsablePass() {
    when(passes.findUsablePass(GUEST)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.start(start("EB-201")))
        .isInstanceOf(NoUsablePassException.class);

    verifyNoInteractions(vehicles, reservations, events);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseWithoutActiveStay() {
    when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.start(start("EB-201")))
        .isInstanceOf(NoActiveStayException.class);

    verifyNoInteractions(passes, vehicles, reservations, events);
  }

  // The failed start left the bike reserved for this pass: the fleet only confirms it still is.
  @Requirement("RIA-54")
  @Test
  void shouldReuseActiveReservationForSameBikeAfterFailedStart() {
    var held = Reservation.hold(PASS, EB_201, NOW.minusSeconds(120), Duration.ofMinutes(15));
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(held));

    var trip = service.start(start("EB-201"));

    assertThat(trip.getReservationId()).isEqualTo(held.getId());
    assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
    verify(vehicles, never()).reserve(anyString());
    verify(reservations, never()).saveAndFlush(any());
    verify(events).publishEvent(new TripStartRequested("EB-201"));
  }

  // A ticket took the held bike out of service and its reservation was not cancelled yet (the
  // listener runs after the ticket commits, or failed): no trip is saved and no unlock is sent.
  @Requirement("RIA-92")
  @Test
  void shouldRefuseToReuseHoldWhoseBikeIsNoLongerReserved() {
    var held = Reservation.hold(PASS, EB_201, NOW.minusSeconds(120), Duration.ofMinutes(15));
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(held));
    doThrow(new VehicleNotBookableException()).when(vehicles).confirmReserved(VEHICLE);

    assertThatThrownBy(() -> service.start(start("EB-201")))
        .isInstanceOf(VehicleNotBookableException.class);

    verify(trips, never()).saveAndFlush(any());
    verifyNoInteractions(events);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseOtherBikeWhileReservationHeld() {
    var held = Reservation.hold(PASS, EB_201, NOW.minusSeconds(120), Duration.ofMinutes(15));
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(held));

    assertThatThrownBy(() -> service.start(start("EB-202")))
        .isInstanceOf(ReservationHeldForAnotherBikeException.class);

    verify(vehicles, never()).reserve(anyString());
    verify(trips, never()).saveAndFlush(any());
    verifyNoInteractions(events);
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotRequestAnUnlockWhenTheBikeCannotBeReserved() {
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(vehicles.reserve("EB-201")).thenThrow(new VehicleNotBookableException());

    assertThatThrownBy(() -> service.start(start("EB-201")))
        .isInstanceOf(VehicleNotBookableException.class);

    verify(trips, never()).saveAndFlush(any());
    verifyNoInteractions(events);
  }

  // The hold of a failed start ended (BR11): the bike is released and another one can be taken.
  @Requirement("RIA-54")
  @Test
  void shouldExpireHeldReservationAndReserveAnotherBikeOnceTheHoldEnded() {
    var held =
        Reservation.hold(PASS, EB_201, NOW.minus(Duration.ofMinutes(15)), Duration.ofMinutes(15));
    var eb202 = new ReservedVehicle(UUID.randomUUID(), "EB-202", HUB);
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(held));
    when(vehicles.reserve("EB-202")).thenReturn(eb202);

    var trip = service.start(start("EB-202"));

    assertThat(held.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    var order = inOrder(reservations, vehicles);
    order.verify(reservations).saveAndFlush(held);
    order.verify(vehicles).release(VEHICLE);
    order.verify(vehicles).reserve("EB-202");
    assertThat(trip.getVehicleCode()).isEqualTo("EB-202");
    assertThat(trip.getReservationId()).isNotEqualTo(held.getId());
  }

  // The same bike after its hold ended is reserved afresh, so its battery is checked again (BR12).
  @Requirement("RIA-54")
  @Test
  void shouldReserveTheSameBikeAfreshOnceItsHoldEnded() {
    var held =
        Reservation.hold(PASS, EB_201, NOW.minus(Duration.ofMinutes(20)), Duration.ofMinutes(15));
    when(reservations.findByPassIdAndStatus(PASS, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(held));
    when(vehicles.reserve("EB-201")).thenReturn(EB_201);

    var trip = service.start(start("EB-201"));

    assertThat(held.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    verify(vehicles).release(VEHICLE);
    verify(vehicles).reserve("EB-201");
    assertThat(trip.getReservationId()).isNotEqualTo(held.getId());
  }
}
