package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

// Unit test (Surefire): the job that fails trips whose unlock was not confirmed in time (RIA-54,
// BR13). Exactly 30 s after the request is already too late. Each overdue trip is checked again
// under its row lock, so a trip that telemetry confirmed in the meantime is left alone. A failed
// trip
// locks the bike again; the reservation, the bike and the pass are not changed (the bike stays
// reserved until the booking expires).
@ExtendWith(MockitoExtension.class)
class UnlockTimeoutJobTest {

  private static final Instant REQUESTED = FixedClockConfiguration.NOW;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final TripsProperties PROPERTIES =
      new TripsProperties(TIMEOUT, Duration.ofMinutes(15));

  @Mock private TripRepository trips;

  @Mock private ApplicationEventPublisher events;

  private final Reservation reservation =
      Reservation.hold(
          PASS,
          new ReservedVehicle(UUID.randomUUID(), "EB-201", HUB),
          REQUESTED,
          Duration.ofMinutes(15));

  private UnlockTimeoutJob jobAt(long secondsAfterRequest) {
    var clock =
        Clock.offset(
            Clock.fixed(REQUESTED, ZoneOffset.UTC), Duration.ofSeconds(secondsAfterRequest));
    return new UnlockTimeoutJob(
        trips, events, clock, PROPERTIES, TransactionOperations.withoutTransaction());
  }

  // The query returns the trip in every case, so the domain's own check under the lock decides.
  @Requirement("RIA-54")
  @ParameterizedTest(name = "{0} s after the request, {1} trip(s) failed")
  @CsvSource({"29, 0", "30, 1", "31, 1"})
  void shouldFailStartingTripOnlyFromThirtySeconds(long seconds, int expectedFailed) {
    var trip = Trip.requestStart(reservation, UUID.randomUUID(), REQUESTED);
    var now = REQUESTED.plusSeconds(seconds);
    when(trips.findIdsByStatusAndRequestedAtLessThanEqual(TripStatus.STARTING, now.minus(TIMEOUT)))
        .thenReturn(List.of(trip.getId()));
    when(trips.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

    var failed = jobAt(seconds).failOverdueStarts();

    assertThat(failed).isEqualTo(expectedFailed);
    if (expectedFailed == 1) {
      assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
      verify(events).publishEvent(new TripFailed("EB-201", HUB));
    } else {
      assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
      verify(events, never()).publishEvent(any(Object.class));
    }
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
  }

  // Telemetry confirmed the unlock between the query and the lock.
  @Requirement("RIA-54")
  @Test
  void shouldLeaveTripConfirmedInTheMeantime() {
    var trip = Trip.requestStart(reservation, UUID.randomUUID(), REQUESTED);
    trip.confirmUnlock(REQUESTED.plusSeconds(29), REQUESTED.plusSeconds(29), TIMEOUT);
    var now = REQUESTED.plusSeconds(35);
    when(trips.findIdsByStatusAndRequestedAtLessThanEqual(TripStatus.STARTING, now.minus(TIMEOUT)))
        .thenReturn(List.of(trip.getId()));
    when(trips.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

    var failed = jobAt(35).failOverdueStarts();

    assertThat(failed).isZero();
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
    verify(events, never()).publishEvent(any(Object.class));
  }

  // One trip that cannot be failed (here, it disappeared) does not stop the others.
  @Requirement("RIA-54")
  @Test
  void shouldGoOnWithTheOtherTripsWhenOneCannotBeFailed() {
    var broken = UUID.randomUUID();
    var trip = Trip.requestStart(reservation, UUID.randomUUID(), REQUESTED);
    var now = REQUESTED.plusSeconds(40);
    when(trips.findIdsByStatusAndRequestedAtLessThanEqual(TripStatus.STARTING, now.minus(TIMEOUT)))
        .thenReturn(List.of(broken, trip.getId()));
    when(trips.findByIdForUpdate(broken)).thenThrow(new IllegalStateException("database hiccup"));
    when(trips.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

    var failed = jobAt(40).failOverdueStarts();

    assertThat(failed).isEqualTo(1);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
  }
}
