package com.riaride.passes.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.passes.internal.domain.UsageRule;
import com.riaride.passes.internal.persistence.UsageRuleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): the rules of use (RIA-96). A change applies from the moment it is made, and
// a trip is charged by the rule in force when it started (BR7). Which rule is in force at a moment
// is the repository's query, checked against PostgreSQL in UsageRuleRepositoryIT.
@ExtendWith(MockitoExtension.class)
class UsageRuleServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");

  @Mock private UsageRuleRepository rules;

  private UsageRuleService service;

  @BeforeEach
  void setUp() {
    service = new UsageRuleService(rules, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Requirement("RIA-96")
  @Test
  void shouldApplyAChangeFromNow() {
    when(rules.saveAndFlush(any(UsageRule.class))).thenAnswer(saved -> saved.getArgument(0));

    var changed = service.change(120);

    assertThat(changed.maxTripDuration()).isEqualTo(Duration.ofHours(2));
    assertThat(changed.getEffectiveFrom()).isEqualTo(NOW);
  }

  // Two changes at the same instant: the unique effective_from lets one through.
  @Requirement("RIA-96")
  @Test
  void shouldRefuseAChangeMadeAtTheSameInstantAsAnother() {
    when(rules.saveAndFlush(any(UsageRule.class)))
        .thenThrow(new DataIntegrityViolationException("uq_usage_rule_effective_from"));

    assertThatThrownBy(() -> service.change(120))
        .isInstanceOf(UsageRuleChangedConcurrentlyException.class);
  }

  @Requirement("RIA-96")
  @Test
  void shouldGiveTheRuleInForceNow() {
    var rule = UsageRule.from(NOW.minusSeconds(60), 150);
    when(rules.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(NOW))
        .thenReturn(Optional.of(rule));

    assertThat(service.current()).isSameAs(rule);
  }

  @Requirement("RIA-96")
  @Test
  void shouldGiveTheMaximumTripDurationInForceWhenATripStarted() {
    var startedAt = NOW.minus(Duration.ofHours(3));
    when(rules.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(startedAt))
        .thenReturn(Optional.of(UsageRule.from(startedAt.minusSeconds(1), 120)));

    assertThat(service.maxTripDurationAt(startedAt)).isEqualTo(Duration.ofHours(2));
  }

  @Requirement("RIA-96")
  @Test
  void shouldFailWhenNoRuleIsInForce() {
    when(rules.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(NOW))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.current()).isInstanceOf(IllegalStateException.class);
  }
}
