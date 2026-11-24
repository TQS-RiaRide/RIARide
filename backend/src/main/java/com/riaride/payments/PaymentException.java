package com.riaride.payments;

import java.util.Optional;

// A payment that did not go through. reason is the provider's error code or status when it gives
// one (for example card_declined or processing), never card data or credentials. paymentRef is the
// provider's payment or refund, when one was created, so the caller can follow it up.
// The provider's own exception is not kept as the cause: its message can repeat the API key.
public abstract sealed class PaymentException extends RuntimeException
    permits TransientPaymentException, PermanentPaymentException {

  private final String reason;
  private final String paymentRef;

  protected PaymentException(String reason, String message, String paymentRef) {
    super(message);
    this.reason = reason;
    this.paymentRef = paymentRef;
  }

  public String reason() {
    return reason;
  }

  public Optional<String> paymentRef() {
    return Optional.ofNullable(paymentRef);
  }
}
