package com.riaride.payments;

// The provider could not be reached or failed on its side (network error, 5xx, rate limit,
// timeout). Retrying later with the same idempotency key is safe.
public final class TransientPaymentException extends PaymentException {

	public TransientPaymentException(String reason, String message, Throwable cause) {
		super(reason, message, cause);
	}

}
