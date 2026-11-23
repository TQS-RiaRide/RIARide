package com.riaride.trips.internal.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling runs UnlockTimeoutJob (riaride.trips.unlock-timeout-check-interval).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TripsProperties.class)
@EnableScheduling
class TripsConfiguration {
}
