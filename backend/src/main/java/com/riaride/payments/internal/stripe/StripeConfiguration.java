package com.riaride.payments.internal.stripe;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StripeProperties.class)
class StripeConfiguration {

	// Test mode only (ADR 0008): a live key would move real money, so startup fails. The message
	// never repeats the key.
	@Bean
	StripePaymentGateway stripePaymentGateway(StripeProperties properties) {
		if (!properties.secretKey().startsWith(StripeProperties.TEST_MODE_PREFIX)) {
			throw new IllegalStateException(
				"riaride.payments.stripe.secret-key must be a Stripe test mode key (sk_test_...)");
		}
		return new StripePaymentGateway(properties);
	}

}
