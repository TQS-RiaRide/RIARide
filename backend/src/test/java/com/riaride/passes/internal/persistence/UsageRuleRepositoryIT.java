package com.riaride.passes.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.passes.internal.domain.UsageRule;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the usage_rule table (Flyway V14) on PostgreSQL in Testcontainers.
// The rule in force at a moment is the latest that applies from then or earlier, so a trip keeps
// the rule it started under (RIA-96). Each test runs in a transaction that is rolled back.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UsageRuleRepositoryIT {

  private static final Instant TEN = Instant.parse("2026-11-30T10:00:00Z");

  @Autowired private UsageRuleRepository rules;

  @Autowired private JdbcTemplate jdbc;

  private Duration inForceAt(Instant at) {
    return rules
        .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(at)
        .orElseThrow()
        .maxTripDuration();
  }

  // BR7: 3 h until a hotel manager changes it.
  @Requirement("RIA-96")
  @Test
  void shouldSeedAMaximumTripDurationOf3Hours() {
    assertThat(inForceAt(TEN)).isEqualTo(Duration.ofHours(3));
  }

  @Requirement("RIA-96")
  @Test
  void shouldGiveTheRuleInForceAtEachMoment() {
    rules.saveAndFlush(UsageRule.from(TEN, 120));
    rules.saveAndFlush(UsageRule.from(TEN.plus(Duration.ofHours(1)), 240));

    assertThat(inForceAt(TEN.minusSeconds(1))).isEqualTo(Duration.ofHours(3));
    assertThat(inForceAt(TEN)).isEqualTo(Duration.ofHours(2));
    assertThat(inForceAt(TEN.plus(Duration.ofMinutes(59)))).isEqualTo(Duration.ofHours(2));
    assertThat(inForceAt(TEN.plus(Duration.ofHours(2)))).isEqualTo(Duration.ofHours(4));
  }

  @Requirement("RIA-96")
  @Test
  void shouldRejectTwoRulesFromTheSameMoment() {
    rules.saveAndFlush(UsageRule.from(TEN, 120));

    assertThatThrownBy(() -> rules.saveAndFlush(UsageRule.from(TEN, 150)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // The limits hold even if the domain is bypassed.
  @Requirement("RIA-96")
  @Test
  void shouldRejectAMaximumTripDurationOutsideTheLimitsInTheDatabase() {
    assertThatThrownBy(() ->
            jdbc.update(
                "insert into usage_rule (id, max_trip_duration_minutes, effective_from)"
                    + " values (gen_random_uuid(), 10, now())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
