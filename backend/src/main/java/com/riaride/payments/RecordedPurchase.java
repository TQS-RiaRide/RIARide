package com.riaride.payments;

import java.util.UUID;

// A purchase payment already recorded for an idempotency key.
public record RecordedPurchase(UUID passId, String paymentRef) {
}
