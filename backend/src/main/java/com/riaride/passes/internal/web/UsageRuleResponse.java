package com.riaride.passes.internal.web;

import com.riaride.passes.internal.domain.UsageRule;
import java.time.Instant;

// effectiveFrom is when the rule took effect: trips that start from then on follow it.
record UsageRuleResponse(int maxTripDurationMinutes, Instant effectiveFrom) {

  static UsageRuleResponse from(UsageRule rule) {
    return new UsageRuleResponse(rule.getMaxTripDurationMinutes(), rule.getEffectiveFrom());
  }
}
