package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): BR12, a vehicle below 20 % battery cannot be booked.
// Boundary values around the threshold: 19 is blocked, 20 and 21 are allowed.
class BookingBatteryPolicyTest {

  private static final BookingBatteryPolicy POLICY = new BookingBatteryPolicy(20);

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(ints = {20, 21, 100})
  void shouldAllowBookingWhenBatteryAtOrAbove20Percent(int percent) {
    var allowed = POLICY.allowsBooking(BatteryLevel.of(percent));

    assertThat(allowed).isTrue();
  }

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(ints = {0, 18, 19})
  void shouldBlockBookingWhenBatteryBelow20Percent(int percent) {
    var allowed = POLICY.allowsBooking(BatteryLevel.of(percent));

    assertThat(allowed).isFalse();
  }

  @Requirement("RIA-59")
  @Test
  void shouldBlockBookingWhenNoBatteryReadingYet() {
    var allowed = POLICY.allowsBooking(null);

    assertThat(allowed).isFalse();
  }

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void shouldRejectMinimumBatteryOutside0To100Percent(int minimumPercent) {
    assertThatThrownBy(() -> new BookingBatteryPolicy(minimumPercent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(String.valueOf(minimumPercent));
  }
}
