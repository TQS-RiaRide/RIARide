package com.riaride.payments;

// The provider could not be reached, failed on its side (network error, 5xx, rate limit,
// timeout), is still processing the payment, or is busy with the same request. Retrying later
// with the same idempotency key is safe; a new key could charge twice.
public final class TransientPaymentException extends PaymentException {

  public TransientPaymentException(String reason, String message, String paymentRef) {
    super(reason, message, paymentRef);
  }
}
