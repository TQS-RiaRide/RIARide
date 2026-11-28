package com.riaride.passes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a pass never lasts past the end of the stay (RIA-87), so a pass without an
// expiry of its own, stored before RIA-87, ends with the stay (RIA-88).
class PassLimitsTest {

  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");

  @Requirement("RIA-88")
  @Test
  void shouldEndWithTheStayWhenThePassHasNoExpiryOfItsOwn() {
    assertThat(new PassLimits(null, STAY_ENDS).expiresAt()).isEqualTo(STAY_ENDS);
  }

  @Requirement("RIA-88")
  @Test
  void shouldRequireTheEndOfTheStay() {
    var expiry = Instant.parse("2026-12-01T10:00:00Z");

    assertThatThrownBy(() -> new PassLimits(expiry, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
