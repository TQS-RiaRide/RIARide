package com.riaride.payments.internal.stripe;

import com.riaride.payments.PaymentException;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.TransientPaymentException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;

// Classifies Stripe errors as transient (worth retrying) or permanent (ADR 0008). Messages
// carry only the Stripe error code and request id: Stripe's own message can repeat the API key.
final class StripeFailures {

	private StripeFailures() {
	}

	static PaymentException classify(StripeException error) {
		var reason = reasonOf(error);
		var message = "Stripe request failed (" + reason + ", request " + error.getRequestId() + ")";
		if (isTransient(error)) {
			return new TransientPaymentException(reason, message, error);
		}
		return new PermanentPaymentException(reason, message, error);
	}

	private static boolean isTransient(StripeException error) {
		// ApiException is Stripe's own server error (api_error).
		if (error instanceof RateLimitException || error instanceof ApiConnectionException
			|| error instanceof ApiException) {
			return true;
		}
		var status = error.getStatusCode();
		return !(error instanceof CardException) && status != null && status >= 500;
	}

	private static String reasonOf(StripeException error) {
		if (error instanceof CardException card && card.getCode() != null) {
			return card.getCode();
		}
		if (error.getCode() != null) {
			return error.getCode();
		}
		if (error instanceof ApiConnectionException) {
			return "network_error";
		}
		return error.getClass().getSimpleName();
	}

}
