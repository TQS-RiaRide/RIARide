package com.riaride.payments;

// A refund the provider accepted. pending means it has not reached the card yet; the provider
// completes it later, so the caller must not refund again.
public record RefundResult(String refundRef, boolean pending) {}
