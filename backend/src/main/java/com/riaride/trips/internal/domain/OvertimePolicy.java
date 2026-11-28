package com.riaride.trips.internal.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

// BR7, BR8 and BR9: overtime is the time a trip runs beyond the earliest of three limits, the
// maximum trip duration, the pass expiry and the end of the stay (architecture, section 7). The
// time beyond the grace period is charged per started 15-minute block. The values are passed in,
// so the rule does not depend on configuration; the Product Owner set 2.00 EUR per block and no
// grace period (RIA-88).
public record OvertimePolicy(Duration maxTripDuration, Duration grace, long pricePerBlockCents) {

  static final Duration BLOCK = Duration.ofMinutes(15);

  public OvertimePolicy {
    if (maxTripDuration == null || !maxTripDuration.isPositive()) {
      throw new IllegalArgumentException("The maximum trip duration must be positive");
    }
    if (grace == null || grace.isNegative()) {
      throw new IllegalArgumentException("The grace period cannot be negative");
    }
    if (pricePerBlockCents <= 0) {
      throw new IllegalArgumentException("The overtime price per block must be positive");
    }
  }

  public Overtime overtimeOf(
      Instant startedAt, Instant endedAt, Instant passExpiresAt, Instant stayEndsAt) {
    if (endedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("A trip cannot end before it starts");
    }
    var limit =
        Stream.of(startedAt.plus(maxTripDuration), passExpiresAt, stayEndsAt)
            .min(Instant::compareTo)
            .orElseThrow();
    var charged = Duration.between(limit.plus(grace), endedAt);
    if (!charged.isPositive()) {
      return Overtime.NONE;
    }
    var blocks = charged.dividedBy(BLOCK);
    if (!charged.minus(BLOCK.multipliedBy(blocks)).isZero()) {
      blocks++;
    }
    return new Overtime(blocks, Math.multiplyExact(blocks, pricePerBlockCents));
  }
}
