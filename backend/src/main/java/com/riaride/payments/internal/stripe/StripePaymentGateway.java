package com.riaride.payments.internal.stripe;

import com.riaride.payments.OffSessionCharge;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.PurchaseRequest;
import com.riaride.payments.PurchaseResult;
import com.riaride.payments.RefundRequest;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

// Stripe test mode adapter (ADR 0008). PaymentIntents are confirmed server-side; the purchase
// creates a Customer and saves the card with setup_future_usage=off_session, and overtime is
// charged off-session to it. The Stripe client never retries: callers decide (see PaymentGateway).
class StripePaymentGateway implements PaymentGateway {

	private static final String SUCCEEDED = "succeeded";
	private static final String REQUIRES_ACTION = "requires_action";

	private final StripeClient stripe;

	StripePaymentGateway(StripeProperties properties) {
		this.stripe = StripeClient.builder()
			.setApiKey(properties.secretKey())
			.setApiBase(properties.apiBase())
			.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
			.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
			.setMaxNetworkRetries(0)
			.build();
	}

	@Override
	public PurchaseResult purchase(PurchaseRequest request) {
		try {
			var customer = stripe.v1().customers().create(
				CustomerCreateParams.builder().setDescription(request.description()).build(),
				idempotent(request.idempotencyKey() + "-customer"));
			var intent = stripe.v1().paymentIntents().create(PaymentIntentCreateParams.builder()
				.setAmount(request.amount().cents())
				.setCurrency(request.amount().currency())
				.setCustomer(customer.getId())
				.setPaymentMethod(request.paymentMethod())
				.setDescription(request.description())
				.setConfirm(true)
				.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
				.setAutomaticPaymentMethods(noRedirects())
				.build(), idempotent(request.idempotencyKey()));
			requireSucceeded(intent);
			return new PurchaseResult(intent.getId(), customer.getId(), intent.getPaymentMethod());
		} catch (StripeException error) {
			throw StripeFailures.classify(error);
		}
	}

	@Override
	public String chargeOffSession(OffSessionCharge charge) {
		try {
			var intent = stripe.v1().paymentIntents().create(PaymentIntentCreateParams.builder()
				.setAmount(charge.amount().cents())
				.setCurrency(charge.amount().currency())
				.setCustomer(charge.customerRef())
				.setPaymentMethod(charge.paymentMethodRef())
				.setConfirm(true)
				.setOffSession(true)
				.build(), idempotent(charge.idempotencyKey()));
			requireSucceeded(intent);
			return intent.getId();
		} catch (StripeException error) {
			throw StripeFailures.classify(error);
		}
	}

	@Override
	public String refund(RefundRequest request) {
		try {
			var refund = stripe.v1().refunds().create(RefundCreateParams.builder()
				.setPaymentIntent(request.paymentRef())
				.setAmount(request.amount().cents())
				.build(), idempotent(request.idempotencyKey()));
			return refund.getId();
		} catch (StripeException error) {
			throw StripeFailures.classify(error);
		}
	}

	// Card payments only, confirmed without a redirect: the MVP has no return page.
	private static PaymentIntentCreateParams.AutomaticPaymentMethods noRedirects() {
		return PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
			.setEnabled(true)
			.setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
			.build();
	}

	// requires_action means 3-D Secure, which the MVP has no step for.
	private static void requireSucceeded(PaymentIntent intent) {
		if (SUCCEEDED.equals(intent.getStatus())) {
			return;
		}
		var reason = REQUIRES_ACTION.equals(intent.getStatus()) ? "authentication_required" : intent.getStatus();
		throw new PermanentPaymentException(reason,
			"Stripe payment " + intent.getId() + " did not succeed (" + intent.getStatus() + ")", null);
	}

	private static RequestOptions idempotent(String key) {
		return RequestOptions.builder().setIdempotencyKey(key).build();
	}

}
