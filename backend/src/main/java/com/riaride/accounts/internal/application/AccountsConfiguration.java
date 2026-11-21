package com.riaride.accounts.internal.application;

import com.riaride.accounts.internal.domain.StayCodeGenerator;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccountsConfiguration {

	// SecureRandom, so stay codes cannot be predicted from earlier ones.
	@Bean
	StayCodeGenerator stayCodeGenerator() {
		return new StayCodeGenerator(new SecureRandom());
	}

}
