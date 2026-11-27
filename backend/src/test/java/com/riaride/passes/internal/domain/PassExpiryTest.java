package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Unit test (Surefire): BR2 and BR4, a pass ends a fixed time after its first trip, never later
// than the end of the guest's stay (RIA-87). The stay ends at the end of the check-out day in Vila
// Ria (Product Owner decision, RIA-87); a 1-day or 3-day pass also ends then if check-out comes
// first.
class PassExpiryTest {

  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
  private static final String KEY = GUEST + ":purchase-1";
  private static final Instant FIRST_TRIP = Instant.parse("2026-11-28T10:00:10Z");
  // Check-out on 2 December: the stay ends at midnight in Vila Ria (UTC+0 in winter).
  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");
  private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  private static Pass paidPass(PassType type, Instant stayEndsAt) {
    var pass = Pass.purchase(GUEST, new PassPrice(type, 2500, 5000), KEY, stayEndsAt);
    pass.confirmPayment();
    return pass;
  }

  private static Pass activePass(PassType type) {
    var pass = paidPass(type, STAY_ENDS);
    pass.activate(FIRST_TRIP);
    return pass;
  }

  // The time runs from the first trip, not from the purchase (BR4).
  @Requirement("RIA-87")
  @Test
  void shouldHaveNoActivationOrExpiryTimeBeforeTheFirstTrip() {
    var pass = paidPass(PassType.ONE_DAY, STAY_ENDS);

    assertThat(pass.getActivatedAt()).isNull();
    assertThat(pass.getExpiresAt()).isNull();
  }

  @Requirement("RIA-87")
  @Test
  void shouldExpireA1DayPass24HoursAfterTheFirstTrip() {
    var pass = activePass(PassType.ONE_DAY);

    assertThat(pass.getExpiresAt()).isEqualTo(FIRST_TRIP.plus(Duration.ofHours(24)));
  }

  @Requirement("RIA-87")
  @Test
  void shouldExpireA3DayPass72HoursAfterTheFirstTrip() {
    var pass = paidPass(PassType.THREE_DAY, Instant.parse("2026-12-10T00:00:00Z"));

    pass.activate(FIRST_TRIP);

    assertThat(pass.getExpiresAt()).isEqualTo(FIRST_TRIP.plus(Duration.ofHours(72)));
  }

  @Requirement("RIA-87")
  @Test
  void shouldEndAStayPassAtTheEndOfTheStay() {
    var pass = activePass(PassType.STAY);

    assertThat(pass.getExpiresAt()).isEqualTo(STAY_ENDS);
  }

  // First trip on 1 December, check-out on the 2nd: the stay ends before the 72 hours are up.
  @Requirement("RIA-87")
  @Test
  void shouldEndA3DayPassAtTheEndOfTheStayWhenCheckOutComesFirst() {
    var pass = paidPass(PassType.THREE_DAY, STAY_ENDS);

    pass.activate(Instant.parse("2026-12-01T10:00:00Z"));

    assertThat(pass.getExpiresAt()).isEqualTo(STAY_ENDS);
  }

  // Boundary: the 24 hours and the end of the stay fall at the same instant.
  @Requirement("RIA-87")
  @Test
  void shouldExpireA1DayPassAtTheEndOfTheStayWhenBothFallTogether() {
    var stayEnds = FIRST_TRIP.plus(Duration.ofHours(24));
    var pass = paidPass(PassType.ONE_DAY, stayEnds);

    pass.activate(FIRST_TRIP);

    assertThat(pass.getExpiresAt()).isEqualTo(stayEnds);
  }

  // A later trip does not move the end of the pass.
  @Requirement("RIA-87")
  @Test
  void shouldKeepTheExpiryOfTheFirstTripOnLaterTrips() {
    var pass = activePass(PassType.ONE_DAY);

    pass.activate(FIRST_TRIP.plus(Duration.ofHours(5)));

    assertThat(pass.getExpiresAt()).isEqualTo(FIRST_TRIP.plus(Duration.ofHours(24)));
  }

  // Boundary values around the expiry: one minute before, at, and one minute after it.
  @Requirement("RIA-87")
  @ParameterizedTest(name = "{0} pass, {1} minutes from expiry: usable = {2}")
  @CsvSource({
    "ONE_DAY, -1, true",
    "ONE_DAY, 0, false",
    "ONE_DAY, 1, false",
    "THREE_DAY, -1, true",
    "THREE_DAY, 0, false",
    "THREE_DAY, 1, false",
    "STAY, -1, true",
    "STAY, 0, false",
    "STAY, 1, false"
  })
  void shouldBeUsableUntilItsExpiryAndNotFromIt(
      PassType type, int minutesFromExpiry, boolean usable) {
    var pass = paidPass(type, Instant.parse("2026-12-10T00:00:00Z"));
    pass.activate(FIRST_TRIP);

    var at = pass.getExpiresAt().plus(ONE_MINUTE.multipliedBy(minutesFromExpiry));

    assertThat(pass.isUsableAt(at)).isEqualTo(usable);
  }

  // A paid pass that was never used cannot take a first trip once the stay has ended (BR1).
  @Requirement("RIA-87")
  @ParameterizedTest(name = "{0} minutes from the end of the stay: usable = {1}")
  @CsvSource({"-1, true", "0, false", "1, false"})
  void shouldNotLetAnUnusedPassStartATripOnceTheStayHasEnded(
      int minutesFromStayEnd, boolean usable) {
    var pass = paidPass(PassType.ONE_DAY, STAY_ENDS);

    var at = STAY_ENDS.plus(ONE_MINUTE.multipliedBy(minutesFromStayEnd));

    assertThat(pass.isUsableAt(at)).isEqualTo(usable);
  }
}
