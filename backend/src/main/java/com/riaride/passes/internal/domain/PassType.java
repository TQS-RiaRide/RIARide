package com.riaride.passes.internal.domain;

import java.time.Duration;
import java.util.Optional;

// BR2: a 1-day pass (24 h after activation), a 3-day pass (72 h, Product Owner decision, RIA-87),
// and a stay pass (until check-out).
public enum PassType {
  ONE_DAY(Duration.ofHours(24)),
  THREE_DAY(Duration.ofHours(72)),
  STAY(null);

  private final Duration duration;

  PassType(Duration duration) {
    this.duration = duration;
  }

  // How long the pass lasts after its first trip; empty for the stay pass, which lasts the stay.
  public Optional<Duration> duration() {
    return Optional.ofNullable(duration);
  }
}
