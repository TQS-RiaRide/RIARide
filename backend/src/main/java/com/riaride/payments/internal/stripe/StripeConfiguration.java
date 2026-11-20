package com.riaride.payments.internal.stripe;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StripeProperties.class)
class StripeConfiguration {

	@Bean
	StripePaymentGateway stripePaymentGateway(StripeProperties properties) {
		return new StripePaymentGateway(properties);
	}

}
