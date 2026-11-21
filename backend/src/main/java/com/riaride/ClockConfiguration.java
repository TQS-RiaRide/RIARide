package com.riaride;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The application's single source of time. Business rules read today's date in Vila Ria's
// time zone from this Clock, never from the system directly, so tests can fix it.
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	static final ZoneId VILA_RIA = ZoneId.of("Europe/Lisbon");

	@Bean
	Clock clock() {
		return Clock.system(VILA_RIA);
	}

}
