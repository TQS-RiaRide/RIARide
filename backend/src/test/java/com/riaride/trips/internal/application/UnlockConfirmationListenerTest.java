package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleReservations;
import com.riaride.passes.PassUsage;
import com.riaride.telemetry.VehicleUnlocked;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

// Unit test (Surefire): an UNLOCKED reading confirms the bike's starting trip (RIA-54). In time,
// the trip is in use, the bike leaves its hub and the first trip activates the pass (BR4). At or
// after 30 seconds, or for a trip that already failed, the bike is locked again instead (BR13).
// Repeated and stale readings change nothing.
@ExtendWith(MockitoExtension.class)
class UnlockConfirmationListenerTest {

  private static final Instant REQUESTED = FixedClockConfiguration.NOW;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0201");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final TripsProperties PROPERTIES =
      new TripsProperties(TIMEOUT, Duration.ofMinutes(15));

  @Mock private TripRepository trips;

  @Mock private ReservationRepository reservations;

  @Mock private VehicleReservations vehicles;

  @Mock private PassUsage passes;

  @Mock private ApplicationEventPublisher events;

  private Reservation reservation;
  private Trip trip;

  @BeforeEach
  void startingTrip() {
    reservation =
        Reservation.hold(
            PASS, new ReservedVehicle(VEHICLE, "EB-201", HUB), REQUESTED, Duration.ofMinutes(15));
    trip = Trip.requestStart(reservation, GUEST, REQUESTED);
    lenient()
        .when(trips.findFirstByVehicleCodeOrderByRequestedAtDesc("EB-201"))
        .thenReturn(Optional.of(trip));
    lenient().when(reservations.findById(reservation.getId())).thenReturn(Optional.of(reservation));
  }

  private UnlockConfirmationListener listenerAt(long secondsAfterRequest) {
    var clock =
        Clock.offset(
            Clock.fixed(REQUESTED, ZoneOffset.UTC), Duration.ofSeconds(secondsAfterRequest));
    return new UnlockConfirmationListener(
        trips, reservations, vehicles, passes, events, clock, PROPERTIES);
  }

  private static VehicleUnlocked unlockedAt(Instant emittedAt) {
    return new VehicleUnlocked("EB-201", UUID.randomUUID().toString(), emittedAt);
  }

  @Requirement("RIA-54")
  @Test
  void shouldStartTripActivatePassAndPutBikeInUse() {
    var now = REQUESTED.plusSeconds(10);
    when(passes.canStartTrip(PASS, now)).thenReturn(true);
    when(vehicles.startRide(VEHICLE)).thenReturn(true);

    listenerAt(10).on(unlockedAt(now.minusSeconds(1)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
    assertThat(trip.getStartedAt()).isEqualTo(now);
    verify(vehicles).startRide(VEHICLE);
    verify(passes).activateOnFirstTrip(PASS, now);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.USED);
    verify(events, never()).publishEvent(any(Object.class));
  }

  // The guest was told the start failed, so a late unlock locks the bike again at its hub; it
  // stays reserved, and the pass is not activated.
  @Requirement("RIA-54")
  @Test
  void shouldFailAndLockWhenUnlockArrivesAfterTimeout() {
    listenerAt(31).on(unlockedAt(REQUESTED.plusSeconds(31)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    verify(events).publishEvent(new TripFailed("EB-201", HUB));
    verify(vehicles, never()).startRide(any());
    verifyNoInteractions(passes);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
  }

  // The timeout counts when the reading is handled: emitted at 29 s but handled at 30 s or later
  // (exactly 30 s is already late), the trip fails and the bike is locked again.
  @Requirement("RIA-54")
  @ParameterizedTest(name = "emitted at 29 s, handled at {0} s")
  @ValueSource(longs = {30, 31})
  void shouldFailAndLockWhenAnUnlockEmittedInTimeIsHandledLate(long handledAt) {
    listenerAt(handledAt).on(unlockedAt(REQUESTED.plusSeconds(29)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    verify(events).publishEvent(new TripFailed("EB-201", HUB));
    verify(vehicles, never()).startRide(any());
    verifyNoInteractions(passes);
  }

  // The timeout job failed the trip and sent LOCK, but the bike unlocked anyway (or the LOCK was
  // lost): every UNLOCKED reading for it sends LOCK again.
  @Requirement("RIA-54")
  @Test
  void shouldLockAgainWhenLatestTripAlreadyFailed() {
    trip.failIfUnlockOverdue(REQUESTED.plusSeconds(30), TIMEOUT);

    listenerAt(45).on(unlockedAt(REQUESTED.plusSeconds(44)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    verify(events).publishEvent(new TripFailed("EB-201", HUB));
    verify(vehicles, never()).startRide(any());
    verifyNoInteractions(passes);
  }

  // The simulator keeps reporting UNLOCKED while the bike is ridden: no second activation.
  @Requirement("RIA-54")
  @Test
  void shouldIgnoreRepeatedUnlockForTripInUse() {
    trip.confirmUnlock(REQUESTED.plusSeconds(5), REQUESTED.plusSeconds(5), TIMEOUT);

    listenerAt(120).on(unlockedAt(REQUESTED.plusSeconds(119)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
    verifyNoInteractions(vehicles, passes, events);
  }

  @Requirement("RIA-54")
  @Test
  void shouldIgnoreUnlockForBikeWithoutTrip() {
    when(trips.findFirstByVehicleCodeOrderByRequestedAtDesc("EB-999")).thenReturn(Optional.empty());

    listenerAt(10)
        .on(new VehicleUnlocked("EB-999", UUID.randomUUID().toString(), REQUESTED.plusSeconds(9)));

    verifyNoInteractions(vehicles, passes, events);
  }

  // A QoS 1 message redelivered from before the request is not an answer to this trip's UNLOCK.
  @Requirement("RIA-54")
  @Test
  void shouldIgnoreUnlockEmittedBeforeTheTripWasRequested() {
    listenerAt(10).on(unlockedAt(REQUESTED.minusSeconds(61)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
    verifyNoInteractions(vehicles, passes, events);
  }

  // An operator took the bike out of service while the unlock was pending. The reading is still
  // handled (no exception rolls back the battery reading): the trip fails and the bike is locked.
  @Requirement("RIA-54")
  @Test
  void shouldFailAndLockWhenTheBikeIsNoLongerReserved() {
    var now = REQUESTED.plusSeconds(10);
    when(passes.canStartTrip(PASS, now)).thenReturn(true);
    when(vehicles.startRide(VEHICLE)).thenReturn(false);

    listenerAt(10).on(unlockedAt(now.minusSeconds(1)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    verify(events).publishEvent(new TripFailed("EB-201", HUB));
    verify(passes, never()).activateOnFirstTrip(any(), any());
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
  }

  // The pass was refunded or cancelled while the unlock was pending: no trip, the bike is locked.
  @Requirement("RIA-54")
  @Test
  void shouldFailAndLockWhenThePassCanNoLongerStartATrip() {
    var now = REQUESTED.plusSeconds(10);
    when(passes.canStartTrip(PASS, now)).thenReturn(false);

    listenerAt(10).on(unlockedAt(now.minusSeconds(1)));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    verify(events).publishEvent(new TripFailed("EB-201", HUB));
    verify(vehicles, never()).startRide(any());
    verify(passes, never()).activateOnFirstTrip(any(), any());
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
  }
}
