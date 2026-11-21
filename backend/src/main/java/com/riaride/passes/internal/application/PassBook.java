package com.riaride.passes.internal.application;

import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.persistence.PassRepository;
import com.riaride.payments.PaymentLedger;
import com.riaride.payments.PurchaseResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Stores a paid pass and its payment in one transaction, so there is never one without the other.
@Component
public class PassBook {

	private final PassRepository passes;
	private final PaymentLedger ledger;

	public PassBook(PassRepository passes, PaymentLedger ledger) {
		this.passes = passes;
		this.ledger = ledger;
	}

	@Transactional
	public void store(Pass pass, String idempotencyKey, PurchaseResult result) {
		passes.saveAndFlush(pass);
		ledger.recordPurchase(pass.getId(), idempotencyKey, pass.amountToCharge(), result);
	}

}
