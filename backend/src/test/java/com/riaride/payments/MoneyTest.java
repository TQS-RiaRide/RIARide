package com.riaride.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): an amount sent to the payment gateway is a positive number of euro cents.
class MoneyTest {

  @Requirement("RIA-52")
  @Test
  void shouldHoldAmountInEuroCents() {
    var money = Money.euros(75, 50);

    assertThat(money.cents()).isEqualTo(7550);
    assertThat(money.currency()).isEqualTo("eur");
  }

  @Requirement("RIA-52")
  @Test
  void shouldAcceptOneCent() {
    assertThat(Money.cents(1).cents()).isEqualTo(1);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectZero() {
    assertThatThrownBy(() -> Money.cents(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldAcceptNinetyNineCents() {
    assertThat(Money.euros(1, 99).cents()).isEqualTo(199);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectCentsAbove99() {
    assertThatThrownBy(() -> Money.euros(1, 100)).isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectNegativeCents() {
    assertThatThrownBy(() -> Money.euros(1, -50)).isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectEurosThatOverflowWhenConvertedToCents() {
    assertThatThrownBy(() -> Money.euros(Long.MAX_VALUE / 100 + 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectNegativeAmount() {
    assertThatThrownBy(() -> Money.cents(-100)).isInstanceOf(IllegalArgumentException.class);
  }
}
