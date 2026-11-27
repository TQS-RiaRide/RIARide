package com.riaride.passes;

import java.time.Duration;
import java.time.Instant;

// Public API of the passes module for the trips module: the rules of use a hotel manager sets from
// the backoffice (RIA-96, architecture section 7).
public interface UsageRules {

  // BR7: how long a trip that started at the given time runs before overtime starts. A change
  // applies to trips that start after it, so a trip in progress keeps the rule it started with, and
  // a charge retried later works out the same amount.
  Duration maxTripDurationAt(Instant tripStartedAt);
}
