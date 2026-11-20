package com.riaride.payments.internal.stripe;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Stripe test mode settings. The secret key comes from the environment (STRIPE_SECRET_KEY),
// never from the repository. apiBase is overridden only by tests, to point at WireMock.
@ConfigurationProperties("riaride.payments.stripe")
record StripeProperties(
	String secretKey,
	@DefaultValue("https://api.stripe.com") String apiBase,
	@DefaultValue("5s") Duration connectTimeout,
	@DefaultValue("20s") Duration readTimeout) {

	@Override
	public String toString() {
		return "StripeProperties[secretKey=********, apiBase=" + apiBase + ", connectTimeout=" + connectTimeout
			+ ", readTimeout=" + readTimeout + "]";
	}

}
