package com.riaride.trips.internal.application;

import com.riaride.trips.internal.domain.OvertimePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling runs UnlockTimeoutJob (riaride.trips.unlock-timeout-check-interval),
// ReservationExpiryJob (riaride.trips.reservation-expiry-check-interval), OvertimeRetryJob
// (riaride.trips.overtime.retry-check-interval) and EndedPassClosingJob
// (riaride.trips.ended-pass-check-interval).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({TripsProperties.class, OvertimeProperties.class})
@EnableScheduling
class TripsConfiguration {

  @Bean
  OvertimePolicy overtimePolicy(OvertimeProperties overtime) {
    return new OvertimePolicy(
        overtime.maxTripDuration(), overtime.grace(), overtime.pricePerBlockCents());
  }
}
