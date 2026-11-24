package com.riaride.trips.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): a trip is STARTING until telemetry confirms the unlock; without that
// confirmation within 30 seconds it FAILS (RIA-54, BR13). Exactly 30 s is already too late. Time
// comes from a fixed Clock, offset per case.
class TripTest {

  private static final Instant REQUESTED = FixedClockConfiguration.NOW;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final UUID GUEST = UUID.fromString("6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0201");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");

  private static Reservation reservation() {
    return Reservation.hold(
        PASS, new ReservedVehicle(VEHICLE, "EB-201", HUB), REQUESTED, Duration.ofMinutes(15));
  }

  private static Trip startingTrip() {
    return Trip.requestStart(reservation(), GUEST, REQUESTED);
  }

  // The instant a Clock fixed at the request time, moved forward by the given seconds, reports.
  private static Instant secondsAfterRequest(long seconds) {
    return Clock.offset(Clock.fixed(REQUESTED, ZoneOffset.UTC), Duration.ofSeconds(seconds))
        .instant();
  }

  @Requirement("RIA-54")
  @Test
  void shouldBeStartingWhenRequested() {
    var reservation = reservation();

    var trip = Trip.requestStart(reservation, GUEST, REQUESTED);

    assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
    assertThat(trip.getGuestId()).isEqualTo(GUEST);
    assertThat(trip.getPassId()).isEqualTo(PASS);
    assertThat(trip.getReservationId()).isEqualTo(reservation.getId());
    assertThat(trip.getVehicleId()).isEqualTo(VEHICLE);
    assertThat(trip.getVehicleCode()).isEqualTo("EB-201");
    assertThat(trip.getHubId()).isEqualTo(HUB);
    assertThat(trip.getRequestedAt()).isEqualTo(REQUESTED);
    assertThat(trip.getStartedAt()).isNull();
  }

  @Requirement("RIA-54")
  @Test
  void shouldStartWhenUnlockConfirmedBeforeTimeout() {
    var trip = startingTrip();
    var now = secondsAfterRequest(29);

    var outcome = trip.confirmUnlock(now, now, TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.STARTED);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
    assertThat(trip.getStartedAt()).isEqualTo(now);
  }

  // An unlock reported once the 30 seconds are over does not start the trip: it fails it, so the
  // bike is locked again.
  @Requirement("RIA-54")
  @ParameterizedTest(name = "unlock {0} s after the request")
  @ValueSource(longs = {30, 31})
  void shouldFailWhenUnlockConfirmedAtOrAfterTimeout(long seconds) {
    var trip = startingTrip();
    var now = secondsAfterRequest(seconds);

    var outcome = trip.confirmUnlock(now, now, TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.TOO_LATE);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    assertThat(trip.getStartedAt()).isNull();
    assertThat(trip.getFailedAt()).isEqualTo(now);
  }

  // The timeout counts when the platform handles the reading, not when the bike emitted it: a
  // reading emitted at 29 s that arrives at 30 s or later (queued while the platform was away) is
  // too late, since the guest may already have been told the start failed.
  @Requirement("RIA-54")
  @ParameterizedTest(name = "emitted at 29 s, handled at {0} s")
  @ValueSource(longs = {30, 31})
  void shouldFailWhenUnlockEmittedInTimeIsHandledAfterTimeout(long handledAt) {
    var trip = startingTrip();

    var outcome =
        trip.confirmUnlock(secondsAfterRequest(29), secondsAfterRequest(handledAt), TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.TOO_LATE);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    assertThat(trip.getStartedAt()).isNull();
  }

  @Requirement("RIA-54")
  @ParameterizedTest(name = "{0} s after the request, overdue = {1}")
  @CsvSource({"29, false", "30, true", "31, true"})
  void unlockTimeoutBoundary(long seconds, boolean overdue) {
    var trip = startingTrip();

    assertThat(trip.isUnlockOverdue(secondsAfterRequest(seconds), TIMEOUT)).isEqualTo(overdue);
  }

  @Requirement("RIA-54")
  @ParameterizedTest(name = "{0} s after the request, failed = {1}")
  @CsvSource({"29, false", "30, true", "31, true"})
  void shouldFailOverdueStartingTrip(long seconds, boolean failed) {
    var trip = startingTrip();
    var now = secondsAfterRequest(seconds);

    var result = trip.failIfUnlockOverdue(now, TIMEOUT);

    assertThat(result).isEqualTo(failed);
    assertThat(trip.getStatus()).isEqualTo(failed ? TripStatus.FAILED : TripStatus.STARTING);
    assertThat(trip.getFailedAt()).isEqualTo(failed ? now : null);
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotFailTripAlreadyInUse() {
    var trip = startingTrip();
    trip.confirmUnlock(secondsAfterRequest(10), secondsAfterRequest(10), TIMEOUT);

    var result = trip.failIfUnlockOverdue(secondsAfterRequest(60), TIMEOUT);

    assertThat(result).isFalse();
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
  }

  // A QoS 1 message can be delivered again later: an UNLOCKED reading emitted before the trip was
  // requested is not an answer to its UNLOCK. One minute of clock skew is allowed, as for battery
  // readings.
  @Requirement("RIA-54")
  @Test
  void shouldIgnoreUnlockEmittedBeforeTripWasRequested() {
    var trip = startingTrip();

    var outcome = trip.confirmUnlock(REQUESTED.minusSeconds(61), secondsAfterRequest(5), TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.STALE);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
    assertThat(trip.getStartedAt()).isNull();
  }

  @Requirement("RIA-54")
  @Test
  void shouldAcceptUnlockStampedUpToOneMinuteBeforeTheRequest() {
    var trip = startingTrip();

    var outcome = trip.confirmUnlock(REQUESTED.minusSeconds(60), secondsAfterRequest(5), TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.STARTED);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
  }

  // The simulator keeps reporting UNLOCKED while the bike is ridden.
  @Requirement("RIA-54")
  @Test
  void shouldTreatRepeatedUnlockAsAlreadyStarted() {
    var trip = startingTrip();
    trip.confirmUnlock(secondsAfterRequest(10), secondsAfterRequest(10), TIMEOUT);

    var outcome = trip.confirmUnlock(secondsAfterRequest(20), secondsAfterRequest(40), TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.ALREADY_STARTED);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
    assertThat(trip.getStartedAt()).isEqualTo(secondsAfterRequest(10));
  }

  // The timeout job failed the trip first; the bike unlocked anyway, so it must be locked again.
  @Requirement("RIA-54")
  @Test
  void shouldReportLateUnlockForFailedTrip() {
    var trip = startingTrip();
    trip.failIfUnlockOverdue(secondsAfterRequest(30), TIMEOUT);

    var outcome = trip.confirmUnlock(secondsAfterRequest(28), secondsAfterRequest(31), TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.TOO_LATE);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    assertThat(trip.getFailedAt()).isEqualTo(secondsAfterRequest(30));
  }

  @Requirement("RIA-54")
  @Test
  void shouldCountStartingAndInUseAsActive() {
    assertThat(TripStatus.ACTIVE).containsExactlyInAnyOrder(TripStatus.STARTING, TripStatus.IN_USE);
  }

  // A public set that any caller could change would change BR6 for the whole application. remove
  // of a status that is not in the set changes nothing, so the check cannot corrupt the set.
  @Requirement("RIA-54")
  @Test
  void shouldNotLetCallersChangeTheActiveStatuses() {
    assertThatThrownBy(() -> TripStatus.ACTIVE.remove(TripStatus.FAILED))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // The unlock came in time, but the bike or the pass could no longer take the trip (RIA-54): the
  // trip fails instead, so the bike is locked again.
  @Requirement("RIA-54")
  @Test
  void shouldFailAStartThatTheBikeOrPassCannotTake() {
    var trip = startingTrip();
    trip.confirmUnlock(secondsAfterRequest(5), secondsAfterRequest(5), TIMEOUT);

    trip.failStart(secondsAfterRequest(5));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
    assertThat(trip.getFailedAt()).isEqualTo(secondsAfterRequest(5));
    assertThat(trip.getStartedAt()).isNull();
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotFailAStartThatAlreadyFailed() {
    var trip = startingTrip();
    trip.failIfUnlockOverdue(secondsAfterRequest(30), TIMEOUT);

    assertThatThrownBy(() -> trip.failStart(secondsAfterRequest(40)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(trip.getFailedAt()).isEqualTo(secondsAfterRequest(30));
  }
}
