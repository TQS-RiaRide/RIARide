package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Unit test (Surefire): BR8 and BR9, a pass that ends during a trip stays open until that trip ends
// (RIA-89). The trip that ends at or after the end of the pass closes it. The deposit stays held:
// its refund at the end of the pass is RIA-114.
class PassClosingTest {

  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
  private static final String KEY = GUEST + ":purchase-1";
  private static final Instant FIRST_TRIP = Instant.parse("2026-11-29T15:00:00Z");
  // Check-out on 2 December: the stay ends at midnight in Vila Ria (RIA-87).
  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");

  private static Pass paidPass(PassType type) {
    var pass = Pass.purchase(GUEST, new PassPrice(type, 2500, 5000), KEY, STAY_ENDS);
    pass.confirmPayment();
    return pass;
  }

  private static Pass activePass(PassType type) {
    var pass = paidPass(type);
    pass.activate(FIRST_TRIP);
    return pass;
  }

  // Boundary values around the end of the pass: a trip that ends one minute before it leaves the
  // pass active; one that ends at it or after it closes the pass.
  @Requirement("RIA-89")
  @ParameterizedTest(
      name = "{0} pass, trip ends {1} minutes from the end of the pass: closed = {2}")
  @CsvSource({
    "ONE_DAY, -1, false",
    "ONE_DAY, 0, true",
    "ONE_DAY, 30, true",
    "STAY, -1, false",
    "STAY, 0, true",
    "STAY, 20, true"
  })
  void shouldCloseOnlyWhenATripEndsAtOrAfterTheEndOfThePass(
      PassType type, int minutesFromEnd, boolean closed) {
    var pass = activePass(type);

    pass.closeIfEndedAt(pass.getExpiresAt().plus(Duration.ofMinutes(minutesFromEnd)));

    assertThat(pass.getStatus()).isEqualTo(closed ? PassStatus.CLOSED : PassStatus.ACTIVE);
  }

  // The pass is past its end while the trip is still in progress, but nothing closes it yet.
  @Requirement("RIA-89")
  @Test
  void shouldStayActiveWhileATripRunsPastTheEndOfThePass() {
    var pass = activePass(PassType.ONE_DAY);

    var duringTheTrip = pass.getExpiresAt().plus(Duration.ofMinutes(10));

    assertThat(pass.isUsableAt(duringTheTrip)).isFalse();
    assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
  }

  @Requirement("RIA-89")
  @Test
  void shouldKeepTheDepositHeldWhenThePassCloses() {
    var pass = activePass(PassType.ONE_DAY);

    pass.closeIfEndedAt(pass.getExpiresAt());

    assertThat(pass.getDepositStatus()).isEqualTo(DepositStatus.HELD);
  }

  @Requirement("RIA-89")
  @Test
  void shouldLeaveAClosedPassClosed() {
    var pass = activePass(PassType.ONE_DAY);
    pass.closeIfEndedAt(pass.getExpiresAt());

    pass.closeIfEndedAt(pass.getExpiresAt().plus(Duration.ofHours(1)));

    assertThat(pass.getStatus()).isEqualTo(PassStatus.CLOSED);
  }

  // Only an active pass has trips to end; a paid pass that was never used is left as it is.
  @Requirement("RIA-89")
  @Test
  void shouldLeaveAPassThatWasNeverUsedAsItIs() {
    var pass = paidPass(PassType.ONE_DAY);

    pass.closeIfEndedAt(STAY_ENDS.plus(Duration.ofMinutes(1)));

    assertThat(pass.getStatus()).isEqualTo(PassStatus.INACTIVE);
  }
}
