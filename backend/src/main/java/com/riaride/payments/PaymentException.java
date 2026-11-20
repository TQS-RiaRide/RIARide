package com.riaride.payments;

// A payment that did not go through. reason is the provider's error code when it gives one
// (for example card_declined), never card data or credentials.
public abstract sealed class PaymentException extends RuntimeException
	permits TransientPaymentException, PermanentPaymentException {

	private final String reason;

	protected PaymentException(String reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public String reason() {
		return reason;
	}

}
