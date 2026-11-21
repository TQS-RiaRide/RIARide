package com.riaride.payments.internal.ledger;

import com.riaride.payments.Money;
import com.riaride.payments.PaymentLedger;
import com.riaride.payments.PurchaseResult;
import com.riaride.payments.RecordedPurchase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JpaPaymentLedger implements PaymentLedger {

	private final PaymentRepository payments;

	JpaPaymentLedger(PaymentRepository payments) {
		this.payments = payments;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<RecordedPurchase> findPurchase(String idempotencyKey) {
		return payments.findByIdempotencyKey(idempotencyKey)
			.filter(payment -> payment.getKind() == Payment.Kind.PURCHASE)
			.map(payment -> new RecordedPurchase(payment.getPassId(), payment.getProviderRef()));
	}

	@Override
	@Transactional
	public void recordPurchase(UUID passId, String idempotencyKey, Money amount, PurchaseResult result) {
		payments.save(Payment.purchase(passId, amount.cents(), result.paymentRef(), idempotencyKey,
			result.customerRef(), result.paymentMethodRef()));
	}

}
