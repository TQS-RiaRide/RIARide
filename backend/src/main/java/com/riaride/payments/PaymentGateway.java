package com.riaride.payments;

// Port to the payment provider (ADR 0008). Every operation takes an idempotency key. A failure
// is a TransientPaymentException, which the caller may retry with the same key, or a
// PermanentPaymentException, which is final. The gateway itself never retries.
public interface PaymentGateway {

  PurchaseResult purchase(PurchaseRequest request);

  // Returns the payment reference.
  String chargeOffSession(OffSessionCharge charge);

  RefundResult refund(RefundRequest request);
}
