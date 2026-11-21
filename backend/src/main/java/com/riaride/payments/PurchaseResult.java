package com.riaride.payments;

// The provider's references, kept with the pass: the payment (for refunds) and the
// customer and saved payment method (for off-session overtime charges).
public record PurchaseResult(String paymentRef, String customerRef, String paymentMethodRef) {
}
