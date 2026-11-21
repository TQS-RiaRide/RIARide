package com.riaride.payments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): every request to the gateway carries an idempotency key and the provider
// references it needs. A null key would be shared by every purchase ("null-customer") or replaced
// by a random one, and a retry could then charge twice.
class PaymentRequestsTest {

	private static final Money AMOUNT = Money.cents(7500);

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void shouldRejectPurchaseWithoutIdempotencyKey(String key) {
		assertThatThrownBy(() -> new PurchaseRequest(AMOUNT, "pm_card_visa", "pass-7f3c2a", key))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectPurchaseWithoutPaymentMethod(String paymentMethod) {
		assertThatThrownBy(() -> new PurchaseRequest(AMOUNT, paymentMethod, "pass-7f3c2a", "purchase-1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectPurchaseWithoutReference(String reference) {
		assertThatThrownBy(() -> new PurchaseRequest(AMOUNT, "pm_card_visa", reference, "purchase-1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldRejectPurchaseWithoutAmount() {
		assertThatThrownBy(() -> new PurchaseRequest(null, "pm_card_visa", "pass-7f3c2a", "purchase-1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void shouldRejectOffSessionChargeWithoutIdempotencyKey(String key) {
		assertThatThrownBy(() -> new OffSessionCharge("cus_rita", "pm_saved_card", Money.cents(250), key))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectOffSessionChargeWithoutCustomer(String customerRef) {
		assertThatThrownBy(() -> new OffSessionCharge(customerRef, "pm_saved_card", Money.cents(250), "overtime-1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectOffSessionChargeWithoutPaymentMethod(String paymentMethodRef) {
		assertThatThrownBy(() -> new OffSessionCharge("cus_rita", paymentMethodRef, Money.cents(250), "overtime-1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void shouldRejectRefundWithoutIdempotencyKey(String key) {
		assertThatThrownBy(() -> new RefundRequest("pi_purchase", Money.cents(5000), key))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-62")
	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectRefundWithoutPayment(String paymentRef) {
		assertThatThrownBy(() -> new RefundRequest(paymentRef, Money.cents(5000), "deposit-refund-1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
