package com.riaride.passes;

import java.time.Instant;

// When a pass stops covering a trip (RIA-88): its expiry (BR8) and the end of the stay (BR9). A
// pass never lasts past the stay (RIA-87), so a pass without an expiry of its own ends with it.
public record PassLimits(Instant expiresAt, Instant stayEndsAt) {

  public PassLimits {
    if (stayEndsAt == null) {
      throw new IllegalArgumentException("A pass ends no later than the stay");
    }
    if (expiresAt == null) {
      expiresAt = stayEndsAt;
    }
  }
}
