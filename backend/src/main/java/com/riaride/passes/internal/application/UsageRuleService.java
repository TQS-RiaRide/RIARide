package com.riaride.passes.internal.application;

import com.riaride.passes.UsageRules;
import com.riaride.passes.internal.domain.UsageRule;
import com.riaride.passes.internal.persistence.UsageRuleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads and changes the rules of use (RIA-96). A change applies from the moment it is made; the
// unique effective_from lets only one of two changes made at the same instant through.
@Service
public class UsageRuleService implements UsageRules {

  private final UsageRuleRepository rules;
  private final Clock clock;

  public UsageRuleService(UsageRuleRepository rules, Clock clock) {
    this.rules = rules;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public UsageRule current() {
    return inForceAt(clock.instant());
  }

  @Transactional
  public UsageRule change(int maxTripDurationMinutes) {
    var rule = UsageRule.from(clock.instant(), maxTripDurationMinutes);
    try {
      return rules.saveAndFlush(rule);
    } catch (DataIntegrityViolationException sameInstant) {
      throw new UsageRuleChangedConcurrentlyException();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Duration maxTripDurationAt(Instant tripStartedAt) {
    return inForceAt(tripStartedAt).maxTripDuration();
  }

  private UsageRule inForceAt(Instant at) {
    return rules
        .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(at)
        .orElseThrow(() -> new IllegalStateException("No rule of use is in force"));
  }
}
