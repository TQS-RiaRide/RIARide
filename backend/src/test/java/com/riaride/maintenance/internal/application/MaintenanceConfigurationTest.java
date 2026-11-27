package com.riaride.maintenance.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Unit test (Surefire): the ticket SLA (RIA-92) comes from configuration and must be positive. A
// zero or negative SLA would make every ticket break sla_due_at > opened_at, so every
// POST /api/v1/tickets would answer 500; the backend refuses to start instead.
class MaintenanceConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(MaintenanceConfiguration.class);

  @Requirement("RIA-92")
  @ParameterizedTest
  @CsvSource({"1ns, PT0.000000001S", "24h, PT24H"})
  void shouldStartWithAPositiveSla(String sla, Duration expected) {
    contextRunner
        .withPropertyValues("riaride.maintenance.sla=" + sla)
        .run(context ->
            assertThat(context.getBean(MaintenanceProperties.class).sla()).isEqualTo(expected));
  }

  @Requirement("RIA-92")
  @ParameterizedTest
  @ValueSource(strings = {"0s", "-24h"})
  void shouldFailStartupWhenSlaIsNotPositive(String sla) {
    contextRunner
        .withPropertyValues("riaride.maintenance.sla=" + sla)
        .run(context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class));
  }

  @Requirement("RIA-92")
  @Test
  void shouldFailStartupWhenSlaIsMissing() {
    contextRunner.run(context ->
        assertThat(context)
            .hasFailed()
            .getFailure()
            .hasRootCauseInstanceOf(BindValidationException.class));
  }
}
