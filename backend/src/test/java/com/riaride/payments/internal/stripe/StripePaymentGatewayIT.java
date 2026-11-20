package com.riaride.payments.internal.stripe;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.riaride.payments.Money;
import com.riaride.payments.OffSessionCharge;
import com.riaride.payments.PaymentException;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.PurchaseRequest;
import com.riaride.payments.RefundRequest;
import com.riaride.payments.RefundResult;
import com.riaride.payments.TransientPaymentException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// Integration test (Failsafe): the Stripe adapter against WireMock stubs of the Stripe API,
// so CI needs no network or Stripe key (ADR 0008, QA Manual 5.2). The stubs cover success and
// both kinds of failure the adapter must classify.
class StripePaymentGatewayIT {

	private static final String API_KEY = "sk_test_wiremock_only";
	private static final String CUSTOMERS = "/v1/customers";
	private static final String PAYMENT_INTENTS = "/v1/payment_intents";
	private static final String REFUNDS = "/v1/refunds";

	private static final String CUSTOMER = """
		{"id": "cus_rita", "object": "customer"}
		""";
	private static final String SUCCEEDED = """
		{"id": "pi_purchase", "object": "payment_intent", "status": "succeeded", "amount": 7500,
		 "currency": "eur", "customer": "cus_rita", "payment_method": "pm_saved_card"}
		""";
	private static final String REQUIRES_ACTION = """
		{"id": "pi_3ds", "object": "payment_intent", "status": "requires_action", "amount": 7500,
		 "currency": "eur", "customer": "cus_rita", "payment_method": "pm_saved_card"}
		""";
	private static final String PROCESSING = """
		{"id": "pi_processing", "object": "payment_intent", "status": "processing", "amount": 7500,
		 "currency": "eur", "customer": "cus_rita", "payment_method": "pm_saved_card"}
		""";
	private static final String REQUIRES_PAYMENT_METHOD = """
		{"id": "pi_refused", "object": "payment_intent", "status": "requires_payment_method", "amount": 7500,
		 "currency": "eur", "customer": "cus_rita"}
		""";
	private static final String CANCELED = """
		{"id": "pi_3ds", "object": "payment_intent", "status": "canceled"}
		""";
	private static final String REFUND = """
		{"id": "re_deposit", "object": "refund", "status": "succeeded", "amount": 5000,
		 "payment_intent": "pi_purchase"}
		""";
	private static final String SERVER_ERROR = """
		{"error": {"type": "api_error", "message": "Something went wrong."}}
		""";

	@RegisterExtension
	static WireMockExtension stripe = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	private StripePaymentGateway gateway;

	@BeforeEach
	void setUp() {
		var properties = new StripeProperties(API_KEY, stripe.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(500));
		gateway = new StripePaymentGateway(properties);
	}

	private static PurchaseRequest purchase() {
		return new PurchaseRequest(Money.euros(75, 0), "pm_card_visa", "pass-7f3c2a", "purchase-1");
	}

	private static String cardError(String code) {
		return """
			{"error": {"type": "card_error", "code": "%s", "decline_code": "%s", "message": "The card was not charged."}}
			""".formatted(code, code);
	}

	private void givenCustomerIsCreated() {
		stripe.stubFor(post(CUSTOMERS).willReturn(okJson(CUSTOMER)));
	}

	@Requirement("RIA-62")
	@Test
	void shouldChargePurchaseAndSaveCardForLaterOffSessionCharges() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED)));

		var result = gateway.purchase(purchase());

		assertThat(result.paymentRef()).isEqualTo("pi_purchase");
		assertThat(result.customerRef()).isEqualTo("cus_rita");
		assertThat(result.paymentMethodRef()).isEqualTo("pm_saved_card");
		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS))
			.withRequestBody(containing("amount=7500"))
			.withRequestBody(containing("currency=eur"))
			.withRequestBody(containing("customer=cus_rita"))
			.withRequestBody(containing("payment_method=pm_card_visa"))
			.withRequestBody(containing("confirm=true"))
			.withRequestBody(containing("setup_future_usage=off_session")));
	}

	// One key per operation, so a retried request never charges twice (ADR 0008).
	@Requirement("RIA-62")
	@Test
	void shouldSendIdempotencyKeysWithEveryRequest() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED)));

		gateway.purchase(purchase());

		stripe.verify(postRequestedFor(urlPathEqualTo(CUSTOMERS))
			.withHeader("Idempotency-Key", equalTo("purchase-1-customer")));
		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS))
			.withHeader("Idempotency-Key", equalTo("purchase-1")));
	}

	@Requirement("RIA-62")
	@Test
	void shouldAuthenticateWithTheConfiguredSecretKey() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED)));

		gateway.purchase(purchase());

		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS))
			.withHeader("Authorization", equalTo("Bearer " + API_KEY)));
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportDeclinedCardAsPermanentFailure() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(402)
			.withHeader("Content-Type", "application/json").withBody(cardError("card_declined"))));

		assertThatThrownBy(() -> gateway.purchase(purchase()))
			.isInstanceOfSatisfying(PermanentPaymentException.class,
				failure -> assertThat(failure.reason()).isEqualTo("card_declined"));
	}

	// The MVP has no UI step for 3-D Secure, so a payment that needs it cannot complete.
	@Requirement("RIA-62")
	@Test
	void shouldReportPurchaseThatNeedsAuthenticationAsPermanentFailure() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(REQUIRES_ACTION)));
		stripe.stubFor(post(PAYMENT_INTENTS + "/pi_3ds/cancel").willReturn(okJson(CANCELED)));

		assertThatThrownBy(() -> gateway.purchase(purchase()))
			.isInstanceOfSatisfying(PermanentPaymentException.class, failure -> {
				assertThat(failure.reason()).isEqualTo("authentication_required");
				assertThat(failure.paymentRef()).contains("pi_3ds");
			});
		// The intent is cancelled, so it does not stay open at Stripe.
		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS + "/pi_3ds/cancel")));
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportStripeServerErrorAsTransientFailure() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(500)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"api_error\", \"message\": \"Something went wrong.\"}}")));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportRateLimitAsTransientFailure() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(429)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"invalid_request_error\", \"code\": \"rate_limit\", \"message\": \"Too many requests.\"}}")));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportDroppedConnectionAsTransientFailure() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportSlowResponseAsTransientFailure() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED).withFixedDelay(2_000)));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
	}

	// The adapter does not retry on its own: the caller decides (ADR 0008).
	@Requirement("RIA-62")
	@Test
	void shouldNotRetryAFailedRequestItself() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(500)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"api_error\", \"message\": \"Something went wrong.\"}}")));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
		stripe.verify(1, postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS)));
	}

	@Requirement("RIA-62")
	@Test
	void shouldChargeOvertimeOffSessionToTheSavedCard() {
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED.replace("pi_purchase", "pi_overtime"))));

		var paymentRef = gateway.chargeOffSession(
			new OffSessionCharge("cus_rita", "pm_saved_card", Money.cents(250), "overtime-1"));

		assertThat(paymentRef).isEqualTo("pi_overtime");
		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS))
			.withHeader("Idempotency-Key", equalTo("overtime-1"))
			.withRequestBody(containing("amount=250"))
			.withRequestBody(containing("customer=cus_rita"))
			.withRequestBody(containing("payment_method=pm_saved_card"))
			.withRequestBody(containing("confirm=true"))
			.withRequestBody(containing("off_session=true"))
			.withRequestBody(notContaining("setup_future_usage")));
	}

	// When the bank asks for authentication, overtime comes out of the deposit instead (ADR 0008).
	@Requirement("RIA-62")
	@Test
	void shouldReportOffSessionChargeThatNeedsAuthenticationAsPermanentFailure() {
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(402)
			.withHeader("Content-Type", "application/json").withBody(cardError("authentication_required"))));
		var charge = new OffSessionCharge("cus_rita", "pm_saved_card", Money.cents(250), "overtime-1");

		assertThatThrownBy(() -> gateway.chargeOffSession(charge))
			.isInstanceOfSatisfying(PermanentPaymentException.class,
				failure -> assertThat(failure.reason()).isEqualTo("authentication_required"));
	}

	@Requirement("RIA-62")
	@Test
	void shouldRefundPartOfAPayment() {
		stripe.stubFor(post(REFUNDS).willReturn(okJson(REFUND)));

		var refund = gateway.refund(new RefundRequest("pi_purchase", Money.cents(5000), "deposit-refund-1"));

		assertThat(refund).isEqualTo(new RefundResult("re_deposit", false));
		stripe.verify(postRequestedFor(urlPathEqualTo(REFUNDS))
			.withHeader("Idempotency-Key", equalTo("deposit-refund-1"))
			.withRequestBody(containing("payment_intent=pi_purchase"))
			.withRequestBody(containing("amount=5000")));
	}

	// Only an opaque reference goes to Stripe, never a guest's name (QA Manual 3.1: personal data).
	@Requirement("RIA-62")
	@Test
	void shouldSendOnlyTheOpaqueReferenceToStripe() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED)));

		gateway.purchase(purchase());

		stripe.verify(postRequestedFor(urlPathEqualTo(CUSTOMERS)).withRequestBody(containing("pass-7f3c2a")));
		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS)).withRequestBody(containing("pass-7f3c2a")));
	}

	// processing may still succeed: a caller that gave up and retried with a new key could charge
	// twice. It is transient, so the caller retries with the same key, and it carries the intent.
	@Requirement("RIA-62")
	@Test
	void shouldReportProcessingPaymentAsTransientWithItsReference() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(PROCESSING)));

		assertThatThrownBy(() -> gateway.purchase(purchase()))
			.isInstanceOfSatisfying(TransientPaymentException.class, failure -> {
				assertThat(failure.reason()).isEqualTo("processing");
				assertThat(failure.paymentRef()).contains("pi_processing");
			});
		stripe.verify(0, postRequestedFor(urlPathMatching(PAYMENT_INTENTS + "/.*/cancel")));
	}

	@Requirement("RIA-62")
	@Test
	void shouldCancelAndReportPaymentLeftWaitingForAnotherCard() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(REQUIRES_PAYMENT_METHOD)));
		stripe.stubFor(post(PAYMENT_INTENTS + "/pi_refused/cancel").willReturn(okJson(CANCELED)));

		assertThatThrownBy(() -> gateway.purchase(purchase()))
			.isInstanceOfSatisfying(PermanentPaymentException.class,
				failure -> assertThat(failure.reason()).isEqualTo("requires_payment_method"));
		stripe.verify(postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS + "/pi_refused/cancel")));
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportCustomerCreationFailureWithoutChargingAnything() {
		stripe.stubFor(post(CUSTOMERS).willReturn(aResponse().withStatus(500)
			.withHeader("Content-Type", "application/json").withBody(SERVER_ERROR)));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
		stripe.verify(0, postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS)));
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportIdempotencyKeyInUseAsTransient() {
		givenCustomerIsCreated();
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(409)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"idempotency_error\", \"code\": \"idempotency_key_in_use\", \"message\": \"There is currently another in-progress request using this key.\"}}")));

		assertThatThrownBy(() -> gateway.purchase(purchase())).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportOffSessionServerErrorAsTransient() {
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(aResponse().withStatus(500)
			.withHeader("Content-Type", "application/json").withBody(SERVER_ERROR)));
		var charge = new OffSessionCharge("cus_rita", "pm_saved_card", Money.cents(250), "overtime-1");

		assertThatThrownBy(() -> gateway.chargeOffSession(charge)).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportSlowOffSessionChargeAsTransient() {
		stripe.stubFor(post(PAYMENT_INTENTS).willReturn(okJson(SUCCEEDED).withFixedDelay(2_000)));
		var charge = new OffSessionCharge("cus_rita", "pm_saved_card", Money.cents(250), "overtime-1");

		assertThatThrownBy(() -> gateway.chargeOffSession(charge)).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportRefundOfUnknownPaymentAsPermanent() {
		stripe.stubFor(post(REFUNDS).willReturn(aResponse().withStatus(404)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"invalid_request_error\", \"code\": \"resource_missing\", \"message\": \"No such payment_intent.\"}}")));
		var refund = new RefundRequest("pi_unknown", Money.cents(5000), "deposit-refund-1");

		assertThatThrownBy(() -> gateway.refund(refund))
			.isInstanceOfSatisfying(PermanentPaymentException.class,
				failure -> assertThat(failure.reason()).isEqualTo("resource_missing"));
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportRefundAboveThePaidAmountAsPermanent() {
		stripe.stubFor(post(REFUNDS).willReturn(aResponse().withStatus(400)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"invalid_request_error\", \"code\": \"charge_exceeds_source_limit\", \"message\": \"Refund amount is greater than the charge.\"}}")));
		var refund = new RefundRequest("pi_purchase", Money.cents(999_999), "deposit-refund-1");

		assertThatThrownBy(() -> gateway.refund(refund)).isInstanceOf(PermanentPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportPendingRefundAsPending() {
		stripe.stubFor(post(REFUNDS).willReturn(okJson(REFUND.replace("\"succeeded\"", "\"pending\""))));

		var refund = gateway.refund(new RefundRequest("pi_purchase", Money.cents(5000), "deposit-refund-1"));

		assertThat(refund).isEqualTo(new RefundResult("re_deposit", true));
	}

	@Requirement("RIA-62")
	@Test
	void shouldReportFailedRefundAsPermanent() {
		stripe.stubFor(post(REFUNDS).willReturn(okJson(REFUND.replace("\"succeeded\"", "\"failed\""))));
		var refund = new RefundRequest("pi_purchase", Money.cents(5000), "deposit-refund-1");

		assertThatThrownBy(() -> gateway.refund(refund))
			.isInstanceOfSatisfying(PermanentPaymentException.class, failure -> {
				assertThat(failure.reason()).isEqualTo("refund_failed");
				assertThat(failure.paymentRef()).contains("re_deposit");
			});
	}

	@Requirement("RIA-62")
	@Test
	void shouldNeverPutTheSecretKeyInAFailureMessage() {
		stripe.stubFor(post(CUSTOMERS).willReturn(aResponse().withStatus(401)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"invalid_request_error\", \"message\": \"Invalid API Key provided: " + API_KEY + "\"}}")));

		assertThatThrownBy(() -> gateway.purchase(purchase()))
			.isInstanceOf(PaymentException.class)
			.satisfies(failure -> {
				for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
					assertThat(String.valueOf(cause.getMessage())).doesNotContain(API_KEY);
				}
			});
	}

}
