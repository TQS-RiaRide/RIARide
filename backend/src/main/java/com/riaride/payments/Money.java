package com.riaride.payments;

// A positive amount in euro cents. The platform charges only in euros.
public record Money(long cents) {

  public static final String CURRENCY = "eur";

  public Money {
    if (cents <= 0) {
      throw new IllegalArgumentException("A payment amount must be positive");
    }
  }

  public static Money cents(long cents) {
    return new Money(cents);
  }

  // cents is the part below one euro, from 0 to 99.
  public static Money euros(long euros, int cents) {
    if (cents < 0 || cents > 99) {
      throw new IllegalArgumentException("Cents must be from 0 to 99");
    }
    try {
      return new Money(Math.addExact(Math.multiplyExact(euros, 100L), cents));
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException("Amount is too large", overflow);
    }
  }

  public String currency() {
    return CURRENCY;
  }
}
