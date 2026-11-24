package com.riaride.trips.internal.domain;

import com.riaride.payments.Money;
import java.util.Optional;

// The overtime of one trip: how many 15-minute blocks are charged, and at what total (BR7).
public record Overtime(long blocks, long amountCents) {

  public static final Overtime NONE = new Overtime(0, 0);

  public Overtime {
    if (blocks < 0 || amountCents < 0) {
      throw new IllegalArgumentException("Overtime cannot be negative");
    }
  }

  // Empty when there is nothing to charge: a payment amount is always positive.
  public Optional<Money> amount() {
    return amountCents == 0 ? Optional.empty() : Optional.of(Money.cents(amountCents));
  }
}
