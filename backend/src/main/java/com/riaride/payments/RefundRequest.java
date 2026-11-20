package com.riaride.payments;

// Refunds all or part of a payment, for example the deposit at the end of a pass.
public record RefundRequest(String paymentRef, Money amount, String idempotencyKey) {
}
