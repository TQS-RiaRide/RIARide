package com.riaride.payments;

// Charges the pass price and deposit and saves the card for later off-session charges.
// paymentMethod is the provider's payment method (a test one in the sandbox); idempotencyKey
// identifies this purchase, so a retry never charges twice.
public record PurchaseRequest(Money amount, String paymentMethod, String description, String idempotencyKey) {
}
