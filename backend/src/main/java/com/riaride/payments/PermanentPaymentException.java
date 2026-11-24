package com.riaride.payments;

// The payment was refused and retrying will not change that (card declined, authentication
// required, invalid request or configuration).
public final class PermanentPaymentException extends PaymentException {

  public PermanentPaymentException(String reason, String message, String paymentRef) {
    super(reason, message, paymentRef);
  }
}
