package com.riaride.payments.internal.stripe;

import com.riaride.payments.PaymentException;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.TransientPaymentException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;

// Classifies Stripe errors as transient (worth retrying with the same key) or permanent
// (ADR 0008). The failure keeps only the Stripe error code, HTTP status and request id, which are
// enough to find the request in the Stripe dashboard. The Stripe exception itself is dropped:
// its message can repeat the API key, and a logged stack trace would show it.
final class StripeFailures {

	static final String IDEMPOTENCY_KEY_IN_USE = "idempotency_key_in_use";

	private StripeFailures() {
	}

	static PaymentException classify(StripeException error) {
		var reason = reasonOf(error);
		var message = "Stripe request failed (" + reason + ", HTTP " + error.getStatusCode() + ", request "
			+ error.getRequestId() + ")";
		if (isTransient(error)) {
			return new TransientPaymentException(reason, message, null);
		}
		return new PermanentPaymentException(reason, message, null);
	}

	private static boolean isTransient(StripeException error) {
		// ApiException is Stripe's own server error (api_error). A key in use belongs to a request
		// still in flight: the retry returns its result.
		if (error instanceof RateLimitException || error instanceof ApiConnectionException
			|| error instanceof ApiException || IDEMPOTENCY_KEY_IN_USE.equals(error.getCode())
			|| error instanceof IdempotencyException && Integer.valueOf(409).equals(error.getStatusCode())) {
			return true;
		}
		var status = error.getStatusCode();
		return !(error instanceof CardException) && status != null && status >= 500;
	}

	private static String reasonOf(StripeException error) {
		if (error.getCode() != null) {
			return error.getCode();
		}
		if (error instanceof ApiConnectionException) {
			return "network_error";
		}
		return error.getClass().getSimpleName();
	}

}
