package com.riaride.payments;

// Charges the card saved at purchase while the guest is not present, for example for overtime.
public record OffSessionCharge(String customerRef, String paymentMethodRef, Money amount, String idempotencyKey) {

	public OffSessionCharge {
		Require.text(customerRef, "customerRef");
		Require.text(paymentMethodRef, "paymentMethodRef");
		Require.present(amount, "amount");
		Require.text(idempotencyKey, "idempotencyKey");
	}

}
