package com.riaride.trips.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.payments.Money;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Unit test (Surefire): BR7, a trip longer than 3 h is charged overtime per started 15-minute block
// after the grace period (RIA-88). Overtime counts from the earliest of three limits: the maximum
// trip duration, the pass expiry (BR8) and the end of the stay (BR9). Product Owner decisions
// (RIA-88): 2.00 EUR per block and no grace period, both configurable.
class OvertimePolicyTest {

  private static final Duration MAX_TRIP = Duration.ofHours(3);
  private static final OvertimePolicy POLICY = new OvertimePolicy(MAX_TRIP, Duration.ZERO, 200);
  private static final Instant STARTED = Instant.parse("2026-11-30T10:00:00Z");
  // Limits that do not apply: the pass and the stay last well beyond the trip.
  private static final Instant FAR = Instant.parse("2026-12-03T00:00:00Z");

  private static Overtime tripOf(OvertimePolicy policy, Duration length) {
    return policy.overtimeOf(STARTED, STARTED.plus(length), FAR, FAR);
  }

  @Requirement("RIA-88")
  @ParameterizedTest(name = "a trip of {0} is charged {1} blocks")
  @CsvSource({
    "PT2H, 0",
    "PT3H, 0",
    "PT3H0M1S, 1",
    "PT3H15M, 1",
    "PT3H15M1S, 2",
    "PT3H20M, 2",
    "PT4H, 4"
  })
  void shouldChargeEachStarted15MinuteBlockBeyond3Hours(Duration length, long blocks) {
    var overtime = tripOf(POLICY, length);

    assertThat(overtime.blocks()).isEqualTo(blocks);
  }

  // The acceptance criterion: 3 h 20 min is 2 blocks, at 2.00 EUR each.
  @Requirement({"RIA-88", "RIA-89"})
  @Test
  void shouldCharge2BlocksForATripOf3Hours20Minutes() {
    var overtime = tripOf(POLICY, Duration.ofMinutes(200));

    assertThat(overtime.blocks()).isEqualTo(2);
    assertThat(overtime.amount()).contains(Money.cents(400));
  }

  @Requirement("RIA-88")
  @Test
  void shouldChargeNothingWithinTheMaximumTripDuration() {
    var overtime = tripOf(POLICY, MAX_TRIP);

    assertThat(overtime.blocks()).isZero();
    assertThat(overtime.amount()).isEmpty();
  }

  // Only the time beyond the grace period is charged, still in started 15-minute blocks.
  @Requirement("RIA-88")
  @ParameterizedTest(name = "with 5 min of grace, a trip of {0} is charged {1} blocks")
  @CsvSource({"PT3H5M, 0", "PT3H5M1S, 1", "PT3H20M, 1", "PT3H20M1S, 2"})
  void shouldApplyTheGracePeriodBeforeCounting(Duration length, long blocks) {
    var withGrace = new OvertimePolicy(MAX_TRIP, Duration.ofMinutes(5), 200);

    assertThat(tripOf(withGrace, length).blocks()).isEqualTo(blocks);
  }

  // BR8: the pass expires 1 h into the trip, so the 2 h after it are overtime.
  @Requirement({"RIA-88", "RIA-89"})
  @Test
  void shouldCountFromThePassExpiryWhenItComesFirst() {
    var overtime =
        POLICY.overtimeOf(
            STARTED, STARTED.plus(Duration.ofHours(3)), STARTED.plus(Duration.ofHours(1)), FAR);

    assertThat(overtime.blocks()).isEqualTo(8);
    assertThat(overtime.amount()).contains(Money.cents(1600));
  }

  // BR9: the stay ends 2 h 40 min into the trip, so the 20 min after it are overtime.
  @Requirement({"RIA-88", "RIA-89"})
  @Test
  void shouldCountFromTheEndOfTheStayWhenItComesFirst() {
    var overtime =
        POLICY.overtimeOf(
            STARTED, STARTED.plus(Duration.ofHours(3)), FAR, STARTED.plus(Duration.ofMinutes(160)));

    assertThat(overtime.blocks()).isEqualTo(2);
  }

  // RIA-89, first acceptance criterion: the 1-day pass expires at 15:00 during a trip that started
  // at 14:00 and ends at 15:30, so the 30 minutes after the expiry are 2 blocks.
  @Requirement("RIA-89")
  @Test
  void shouldCharge2BlocksForATripThatEnds30MinutesAfterThePassExpires() {
    var started = Instant.parse("2026-11-30T14:00:00Z");
    var expires = Instant.parse("2026-11-30T15:00:00Z");

    var overtime = POLICY.overtimeOf(started, Instant.parse("2026-11-30T15:30:00Z"), expires, FAR);

    assertThat(overtime.blocks()).isEqualTo(2);
    assertThat(overtime.amount()).contains(Money.cents(400));
  }

  // RIA-89, second acceptance criterion: a stay pass ends with the stay, during a trip that ends 20
  // minutes later. The pass expiry and the end of the stay are the same instant, counted once.
  @Requirement("RIA-89")
  @Test
  void shouldCountOnceFromTheEndOfAStayPassThatIsAlsoTheEndOfTheStay() {
    var stayEnds = STARTED.plus(Duration.ofMinutes(30));

    var overtime =
        POLICY.overtimeOf(STARTED, stayEnds.plus(Duration.ofMinutes(20)), stayEnds, stayEnds);

    assertThat(overtime.blocks()).isEqualTo(2);
  }

  // Two limits at the same instant: the pass expires exactly when the 3 hours are up.
  @Requirement("RIA-89")
  @ParameterizedTest(name = "pass expiry at the 3 h limit, trip ends {0} after it: {1} blocks")
  @CsvSource({"PT0S, 0", "PT1S, 1", "PT15M, 1", "PT20M, 2"})
  void shouldCountOnceFromTheMaximumDurationAndThePassExpiryWhenTheyFallTogether(
      Duration afterTheLimit, long blocks) {
    var limit = STARTED.plus(MAX_TRIP);

    var overtime = POLICY.overtimeOf(STARTED, limit.plus(afterTheLimit), limit, FAR);

    assertThat(overtime.blocks()).isEqualTo(blocks);
  }

  @Requirement("RIA-88")
  @Test
  void shouldRefuseATripThatEndsBeforeItStarts() {
    var before = STARTED.minusSeconds(1);

    assertThatThrownBy(() -> POLICY.overtimeOf(STARTED, before, FAR, FAR))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-88")
  @Test
  void shouldRefuseAPolicyWithoutAPositiveMaximumDuration() {
    assertThatThrownBy(() -> new OvertimePolicy(Duration.ZERO, Duration.ZERO, 200))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-88")
  @Test
  void shouldRefuseANegativeGracePeriod() {
    assertThatThrownBy(() -> new OvertimePolicy(MAX_TRIP, Duration.ofMinutes(-1), 200))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-88")
  @Test
  void shouldRefuseABlockPriceThatIsNotPositive() {
    assertThatThrownBy(() -> new OvertimePolicy(MAX_TRIP, Duration.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
