package com.riaride.payments;

import java.util.UUID;

// Public API of the payments module for the trips module: charges a trip's overtime off-session,
// to the card saved when the pass was bought (RIA-88, ADR 0008).
public interface OvertimeCharges {

  // Once per idempotency key: a key already charged, or refused for good, is not tried again. A
  // refusal (card declined, authentication required) is recorded as FAILED and returned; its
  // deposit fallback is RIA-114. A TransientPaymentException is thrown with nothing recorded, so
  // the caller retries with the same key.
  OvertimeChargeResult charge(UUID passId, Money amount, String idempotencyKey);

  // Records overtime whose last charge attempt failed for now too as FAILED, so the deposit
  // fallback (RIA-114) and the operator dashboard find it. A key already recorded is left as it
  // is.
  void recordUnpaid(UUID passId, Money amount, String idempotencyKey);
}
