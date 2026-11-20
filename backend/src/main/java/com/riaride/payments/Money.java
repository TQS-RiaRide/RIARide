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

	public static Money euros(long euros, int cents) {
		return new Money(euros * 100 + cents);
	}

	public String currency() {
		return CURRENCY;
	}

}
