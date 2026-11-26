package com.riaride.trips.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a trip in use ends at a hub (RIA-55, BR10); a trip that is starting, has
// failed or has already ended cannot end. An UNLOCKED reading that arrives after the end, sent
// while the bike was ridden, changes nothing.
class TripEndTest {

  private static final Instant REQUESTED = FixedClockConfiguration.NOW;
  private static final Instant STARTED = REQUESTED.plusSeconds(5);
  private static final Instant ENDED = REQUESTED.plus(Duration.ofMinutes(40));
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final UUID GUEST = UUID.fromString("6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0301");
  private static final UUID START_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID END_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a002");

  private static Trip startingTrip() {
    var reservation =
        Reservation.hold(
            PASS,
            new ReservedVehicle(VEHICLE, "EB-301", START_HUB),
            REQUESTED,
            Duration.ofMinutes(15));
    return Trip.requestStart(reservation, GUEST, REQUESTED);
  }

  private static Trip tripInUse() {
    var trip = startingTrip();
    trip.confirmUnlock(STARTED, STARTED, TIMEOUT);
    return trip;
  }

  @Requirement("RIA-55")
  @Test
  void shouldEndATripInUseAtTheHub() {
    var trip = tripInUse();

    trip.end(END_HUB, ENDED);

    assertThat(trip.getStatus()).isEqualTo(TripStatus.ENDED);
    assertThat(trip.getEndedAt()).isEqualTo(ENDED);
    assertThat(trip.getEndHubId()).isEqualTo(END_HUB);
    assertThat(trip.getStartedAt()).isEqualTo(STARTED);
    assertThat(trip.getHubId()).isEqualTo(START_HUB);
  }

  @Requirement("RIA-55")
  @Test
  void shouldNotEndATripThatIsStillStarting() {
    var trip = startingTrip();

    assertThatThrownBy(() -> trip.end(END_HUB, ENDED)).isInstanceOf(IllegalStateException.class);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
    assertThat(trip.getEndedAt()).isNull();
  }

  @Requirement("RIA-55")
  @Test
  void shouldNotEndAFailedTrip() {
    var trip = startingTrip();
    trip.failIfUnlockOverdue(REQUESTED.plus(TIMEOUT), TIMEOUT);

    assertThatThrownBy(() -> trip.end(END_HUB, ENDED)).isInstanceOf(IllegalStateException.class);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
  }

  @Requirement("RIA-55")
  @Test
  void shouldNotEndATripTwice() {
    var trip = tripInUse();
    trip.end(END_HUB, ENDED);

    assertThatThrownBy(() -> trip.end(START_HUB, ENDED.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(trip.getEndHubId()).isEqualTo(END_HUB);
    assertThat(trip.getEndedAt()).isEqualTo(ENDED);
  }

  @Requirement("RIA-55")
  @Test
  void shouldNeedAHubAndATime() {
    var trip = tripInUse();

    assertThatThrownBy(() -> trip.end(null, ENDED)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> trip.end(END_HUB, null)).isInstanceOf(IllegalArgumentException.class);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
  }

  // The simulator repeats UNLOCKED while the bike is ridden; one of those readings may only be
  // handled after the guest ended the trip. It must not bring the trip back into use.
  @Requirement("RIA-55")
  @Test
  void shouldIgnoreAnUnlockedReadingHandledAfterTheEnd() {
    var trip = tripInUse();
    trip.end(END_HUB, ENDED);

    var outcome = trip.confirmUnlock(ENDED.minusSeconds(10), ENDED.plusSeconds(2), TIMEOUT);

    assertThat(outcome).isEqualTo(UnlockOutcome.ALREADY_ENDED);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.ENDED);
  }
}
