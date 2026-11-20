package com.riaride.payments.internal.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.TransientPaymentException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): Stripe errors become transient failures, which the caller may retry,
// or permanent ones, which are final (ADR 0008).
class StripeFailuresTest {

	@Requirement("RIA-62")
	@Test
	void shouldClassifyDeclinedCardAsPermanentWithItsCode() {
		var declined = new CardException("Your card was declined.", "req_1", "card_declined", null,
			"generic_decline", null, 402, null);

		var failure = StripeFailures.classify(declined);

		assertThat(failure).isInstanceOf(PermanentPaymentException.class);
		assertThat(failure.reason()).isEqualTo("card_declined");
	}

	@Requirement("RIA-62")
	@Test
	void shouldClassifyAuthenticationRequiredAsPermanent() {
		var authenticationRequired = new CardException("This payment requires authentication.", "req_2",
			"authentication_required", null, "authentication_required", null, 402, null);

		var failure = StripeFailures.classify(authenticationRequired);

		assertThat(failure).isInstanceOf(PermanentPaymentException.class);
		assertThat(failure.reason()).isEqualTo("authentication_required");
	}

	@Requirement("RIA-62")
	@Test
	void shouldClassifyRateLimitAsTransient() {
		var rateLimited = new RateLimitException("Too many requests", "req_3", "rate_limit", null, 429, null);

		assertThat(StripeFailures.classify(rateLimited)).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldClassifyNetworkErrorAsTransient() {
		var network = new ApiConnectionException("Connection reset");

		assertThat(StripeFailures.classify(network)).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldClassifyStripeServerErrorAsTransient() {
		var serverError = new ApiException("Internal error", "req_4", null, 500, null);

		assertThat(StripeFailures.classify(serverError)).isInstanceOf(TransientPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldClassifyInvalidRequestAsPermanent() {
		var invalid = new InvalidRequestException("No such payment_intent", "req_5", "resource_missing", null, 404,
			null);

		assertThat(StripeFailures.classify(invalid)).isInstanceOf(PermanentPaymentException.class);
	}

	// A wrong or missing API key is a configuration error; retrying does not fix it.
	@Requirement("RIA-62")
	@Test
	void shouldClassifyInvalidApiKeyAsPermanent() {
		var badKey = new AuthenticationException("Invalid API Key provided", "req_6", null, 401);

		assertThat(StripeFailures.classify(badKey)).isInstanceOf(PermanentPaymentException.class);
	}

	@Requirement("RIA-62")
	@Test
	void shouldClassifyReusedIdempotencyKeyAsPermanent() {
		var reused = new IdempotencyException("Keys for idempotent requests can only be used with the same parameters",
			"req_7", "idempotency_key_in_use", 400);

		assertThat(StripeFailures.classify(reused)).isInstanceOf(PermanentPaymentException.class);
	}

	// Stripe returns 409 while a request with the same key is still in flight; retrying with the
	// same key is safe and returns the original result.
	@Requirement("RIA-62")
	@Test
	void shouldClassifyIdempotencyKeyInUseAsTransient() {
		var inFlight = new IdempotencyException("There is currently another in-progress request using this key",
			"req_8", "idempotency_key_in_use", 409);

		assertThat(StripeFailures.classify(inFlight)).isInstanceOf(TransientPaymentException.class);
	}

	// Stripe's own message can repeat the API key, so the Stripe exception is not kept as the cause:
	// a logged stack trace would show it.
	@Requirement("RIA-62")
	@Test
	void shouldNotKeepTheStripeErrorWhoseMessageMayRepeatTheKey() {
		var badKey = new AuthenticationException("Invalid API Key provided: sk_test_secret", "req_9", null, 401);

		var failure = StripeFailures.classify(badKey);

		assertThat(failure.getCause()).isNull();
		assertThat(failure.getMessage()).doesNotContain("sk_test_secret").contains("req_9").contains("401");
	}

}
