package com.riaride.payments;

// Refunds all or part of a payment, for example the deposit at the end of a pass.
public record RefundRequest(String paymentRef, Money amount, String idempotencyKey) {

  public RefundRequest {
    Require.text(paymentRef, "paymentRef");
    Require.present(amount, "amount");
    Require.text(idempotencyKey, "idempotencyKey");
  }
}
