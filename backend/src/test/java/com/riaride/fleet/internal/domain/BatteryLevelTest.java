package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): a battery level is a percentage reported by telemetry, from 0 to 100.
class BatteryLevelTest {

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(ints = {0, 20, 100})
  void shouldAcceptBatteryLevelFrom0To100Percent(int percent) {
    var level = BatteryLevel.of(percent);

    assertThat(level.percent()).isEqualTo(percent);
  }

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void shouldRejectBatteryLevelOutside0To100Percent(int percent) {
    assertThatThrownBy(() -> BatteryLevel.of(percent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(String.valueOf(percent));
  }
}
