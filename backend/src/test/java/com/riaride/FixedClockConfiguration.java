package com.riaride;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Fixed time for tests whose outcome depends on today's date, so they do not start
// failing once the calendar passes their example dates (QA Manual: fixed clocks, no sleeps).
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

  // 30 November 2026, 10:00 in Vila Ria.
  public static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
  public static final ZoneId VILA_RIA = ZoneId.of("Europe/Lisbon");

  @Bean
  @Primary
  Clock fixedClock() {
    return Clock.fixed(NOW, VILA_RIA);
  }
}
