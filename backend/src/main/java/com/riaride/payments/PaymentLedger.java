package com.riaride.payments;

import java.util.Optional;
import java.util.UUID;

// Public API of the payments module: the record of every charge, keyed by its idempotency key,
// so a retried operation is recognised and never charged twice (architecture, "No duplicate charge").
public interface PaymentLedger {

	Optional<RecordedPurchase> findPurchase(String idempotencyKey);

	// Records a successful purchase of a pass, with the provider references needed later for
	// the deposit refund and off-session overtime charges. Joins the caller's transaction.
	void recordPurchase(UUID passId, String idempotencyKey, Money amount, PurchaseResult result);

}
