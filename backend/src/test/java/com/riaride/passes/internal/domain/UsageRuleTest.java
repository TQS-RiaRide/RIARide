package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): the maximum trip duration a hotel manager sets (RIA-96, BR7), in whole
// minutes from a quarter of an hour (the overtime block) to a day.
class UsageRuleTest {

  private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");

  // The acceptance criterion changes it to 2 hours.
  @Requirement("RIA-96")
  @ParameterizedTest(name = "{0} minutes is a valid maximum")
  @ValueSource(ints = {15, 120, 180, 1440})
  void shouldKeepAMaximumTripDurationWithinTheLimits(int minutes) {
    var rule = UsageRule.from(NOW, minutes);

    assertThat(rule.maxTripDuration()).isEqualTo(Duration.ofMinutes(minutes));
    assertThat(rule.getEffectiveFrom()).isEqualTo(NOW);
  }

  @Requirement("RIA-96")
  @ParameterizedTest(name = "{0} minutes is refused")
  @ValueSource(ints = {14, 0, -60, 1441})
  void shouldRefuseAMaximumTripDurationOutsideTheLimits(int minutes) {
    assertThatThrownBy(() -> UsageRule.from(NOW, minutes))
        .isInstanceOfSatisfying(
            InvalidUsageRuleException.class,
            invalid -> {
              assertThat(invalid.getField()).isEqualTo("maxTripDurationMinutes");
              assertThat(invalid.getMessage()).doesNotContain(String.valueOf(minutes));
            });
  }

  @Requirement("RIA-96")
  @Test
  void shouldNeedTheMomentTheRuleAppliesFrom() {
    assertThatThrownBy(() -> UsageRule.from(null, 120))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
