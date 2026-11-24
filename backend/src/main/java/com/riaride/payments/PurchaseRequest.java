package com.riaride.payments;

// Charges the pass price and deposit and saves the card for later off-session charges.
// paymentMethod is the provider's payment method (a test one in the sandbox). reference is an
// opaque id, such as the pass id, sent to the provider to find the payment again: never a name
// or other personal data. idempotencyKey identifies this purchase, so a retry never charges twice.
public record PurchaseRequest(
    Money amount, String paymentMethod, String reference, String idempotencyKey) {

  public PurchaseRequest {
    Require.present(amount, "amount");
    Require.text(paymentMethod, "paymentMethod");
    Require.text(reference, "reference");
    Require.text(idempotencyKey, "idempotencyKey");
  }
}
