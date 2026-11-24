package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleReservations;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
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
import org.springframework.transaction.support.TransactionOperations;

// Unit test (Surefire): the job that ends the holds that were not used (RIA-54, BR11). Exactly 15
// minutes after the reservation the hold is over: the reservation expires and its bike is available
// again, so a failed start (BR13) never keeps the bike or the pass stuck. Each reservation is
// checked again under its row lock; one whose trip is still starting is left alone.
@ExtendWith(MockitoExtension.class)
class ReservationExpiryJobTest {

  private static final Instant RESERVED = FixedClockConfiguration.NOW;
  private static final Duration HOLD = Duration.ofMinutes(15);
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0201");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");

  @Mock private ReservationRepository reservations;

  @Mock private TripRepository trips;

  @Mock private VehicleReservations vehicles;

  private final Reservation reservation =
      Reservation.hold(PASS, new ReservedVehicle(VEHICLE, "EB-201", HUB), RESERVED, HOLD);

  private ReservationExpiryJob jobAt(Duration afterReservation) {
    var clock = Clock.offset(Clock.fixed(RESERVED, ZoneOffset.UTC), afterReservation);
    return new ReservationExpiryJob(
        reservations, trips, vehicles, clock, TransactionOperations.withoutTransaction());
  }

  // The query returns the reservation in every case, so the domain's own check under the lock
  // decides.
  @Requirement("RIA-54")
  @ParameterizedTest(name = "{0} s after the reservation, {1} expired")
  @CsvSource({"899, 0", "900, 1", "901, 1"})
  void shouldExpireReservationOnlyFrom15Minutes(long seconds, int expectedExpired) {
    var now = RESERVED.plusSeconds(seconds);
    when(reservations.findIdsByStatusAndExpiresAtLessThanEqual(ReservationStatus.ACTIVE, now))
        .thenReturn(List.of(reservation.getId()));
    when(reservations.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));

    var expired = jobAt(Duration.ofSeconds(seconds)).expireOverdue();

    assertThat(expired).isEqualTo(expectedExpired);
    if (expectedExpired == 1) {
      assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
      verify(vehicles).release(VEHICLE);
    } else {
      assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
      verify(vehicles, never()).release(any());
    }
  }

  // The guest retried just before the hold ended: the unlock decides that trip, not this job.
  @Requirement("RIA-54")
  @Test
  void shouldLeaveReservationWhoseTripIsStillStarting() {
    var now = RESERVED.plus(HOLD);
    when(reservations.findIdsByStatusAndExpiresAtLessThanEqual(ReservationStatus.ACTIVE, now))
        .thenReturn(List.of(reservation.getId()));
    when(reservations.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
    when(trips.existsByReservationIdAndStatus(reservation.getId(), TripStatus.STARTING))
        .thenReturn(true);

    var expired = jobAt(HOLD).expireOverdue();

    assertThat(expired).isZero();
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    verify(vehicles, never()).release(any());
  }

  // A trip started on it in the meantime: the reservation was used and the bike is ridden.
  @Requirement("RIA-54")
  @Test
  void shouldLeaveReservationUsedInTheMeantime() {
    var now = RESERVED.plus(HOLD);
    reservation.markUsed();
    when(reservations.findIdsByStatusAndExpiresAtLessThanEqual(ReservationStatus.ACTIVE, now))
        .thenReturn(List.of(reservation.getId()));
    when(reservations.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));

    var expired = jobAt(HOLD).expireOverdue();

    assertThat(expired).isZero();
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.USED);
    verify(vehicles, never()).release(any());
  }

  // One reservation that cannot be expired does not stop the others.
  @Requirement("RIA-54")
  @Test
  void shouldExpireTheOthersWhenOneFails() {
    var now = RESERVED.plus(HOLD);
    var broken = UUID.randomUUID();
    when(reservations.findIdsByStatusAndExpiresAtLessThanEqual(ReservationStatus.ACTIVE, now))
        .thenReturn(List.of(broken, reservation.getId()));
    when(reservations.findByIdForUpdate(broken)).thenThrow(new IllegalStateException("broken"));
    when(reservations.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));

    var expired = jobAt(HOLD).expireOverdue();

    assertThat(expired).isEqualTo(1);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
  }
}
