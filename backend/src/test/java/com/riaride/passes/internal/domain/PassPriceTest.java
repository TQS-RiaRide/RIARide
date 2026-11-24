package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the base price of a pass type, one for the whole hotel group (BR3).
class PassPriceTest {

  @Requirement("RIA-52")
  @Test
  void shouldAcceptPositivePriceAndDeposit() {
    var price = new PassPrice(PassType.THREE_DAY, 1, 1);

    assertThat(price.getPriceCents()).isEqualTo(1);
    assertThat(price.getDepositCents()).isEqualTo(1);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectZeroPrice() {
    assertThatThrownBy(() -> new PassPrice(PassType.STAY, 0, 5000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectZeroDeposit() {
    assertThatThrownBy(() -> new PassPrice(PassType.STAY, 2500, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectNegativeValuesWhenChanged() {
    var price = new PassPrice(PassType.STAY, 2500, 5000);

    assertThatThrownBy(() -> price.change(-1, 5000)).isInstanceOf(IllegalArgumentException.class);
  }
}
