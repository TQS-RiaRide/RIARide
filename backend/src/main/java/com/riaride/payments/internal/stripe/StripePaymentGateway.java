package com.riaride.payments.internal.stripe;

import com.riaride.payments.OffSessionCharge;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.PurchaseRequest;
import com.riaride.payments.PurchaseResult;
import com.riaride.payments.RefundRequest;
import com.riaride.payments.RefundResult;
import com.riaride.payments.TransientPaymentException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Stripe test mode adapter (ADR 0008). PaymentIntents are confirmed server-side; the purchase
// creates a Customer and saves the card with setup_future_usage=off_session, and overtime is
// charged off-session to it. The Stripe client never retries: callers decide (see PaymentGateway).
class StripePaymentGateway implements PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

	private static final String SUCCEEDED = "succeeded";
	private static final String PROCESSING = "processing";
	private static final String REQUIRES_ACTION = "requires_action";
	// Statuses in which Stripe still lets an intent be cancelled.
	private static final Set<String> CANCELABLE = Set.of("requires_payment_method", "requires_confirmation",
		REQUIRES_ACTION, "requires_capture");

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

	// A Customer is created first. If the payment then fails permanently, that Customer stays in
	// Stripe with no payment: harmless in test mode, and a retry with the same key reuses it.
	@Override
	public PurchaseResult purchase(PurchaseRequest request) {
		try {
			var customer = stripe.v1().customers().create(
				CustomerCreateParams.builder()
					.setDescription("RiaRide " + request.reference())
					.putMetadata("reference", request.reference())
					.build(),
				idempotent(request.idempotencyKey() + "-customer"));
			var intent = stripe.v1().paymentIntents().create(PaymentIntentCreateParams.builder()
				.setAmount(request.amount().cents())
				.setCurrency(request.amount().currency())
				.setCustomer(customer.getId())
				.setPaymentMethod(request.paymentMethod())
				.setDescription("RiaRide " + request.reference())
				.putMetadata("reference", request.reference())
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
	public RefundResult refund(RefundRequest request) {
		try {
			var refund = stripe.v1().refunds().create(RefundCreateParams.builder()
				.setPaymentIntent(request.paymentRef())
				.setAmount(request.amount().cents())
				.build(), idempotent(request.idempotencyKey()));
			return switch (refund.getStatus()) {
				case SUCCEEDED -> new RefundResult(refund.getId(), false);
				case "failed", "canceled" -> throw new PermanentPaymentException("refund_failed",
					"Stripe refund " + refund.getId() + " " + refund.getStatus(), refund.getId());
				// pending or requires_action: Stripe completes it later.
				default -> new RefundResult(refund.getId(), true);
			};
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

	// processing may still succeed, so it is transient and keeps the intent: the caller retries with
	// the same key and gets the same intent. Any other status is final; the intent is cancelled so it
	// does not stay open at Stripe. requires_action means 3-D Secure, which the MVP has no step for.
	private void requireSucceeded(PaymentIntent intent) {
		var status = intent.getStatus();
		if (SUCCEEDED.equals(status)) {
			return;
		}
		if (PROCESSING.equals(status)) {
			throw new TransientPaymentException(PROCESSING, "Stripe payment " + intent.getId() + " is processing",
				intent.getId());
		}
		cancelQuietly(intent);
		var reason = REQUIRES_ACTION.equals(status) ? "authentication_required" : status;
		throw new PermanentPaymentException(reason,
			"Stripe payment " + intent.getId() + " did not succeed (" + status + ")", intent.getId());
	}

	private void cancelQuietly(PaymentIntent intent) {
		if (!CANCELABLE.contains(intent.getStatus())) {
			return;
		}
		try {
			stripe.v1().paymentIntents().cancel(intent.getId());
		} catch (StripeException failure) {
			log.warn("Could not cancel Stripe payment {} ({})", intent.getId(), failure.getCode());
		}
	}

	private static RequestOptions idempotent(String key) {
		return RequestOptions.builder().setIdempotencyKey(key).build();
	}

}
