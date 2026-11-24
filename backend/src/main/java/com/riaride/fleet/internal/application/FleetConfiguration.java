package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FleetProperties.class)
class FleetConfiguration {

  @Bean
  BookingBatteryPolicy bookingBatteryPolicy(FleetProperties properties) {
    return new BookingBatteryPolicy(properties.minBookingBatteryPct());
  }
}
