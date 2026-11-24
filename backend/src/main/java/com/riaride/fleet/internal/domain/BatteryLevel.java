package com.riaride.fleet.internal.domain;

/** A vehicle's battery charge as a percentage, as reported by telemetry. */
public record BatteryLevel(int percent) {

  private static final int MIN_PERCENT = 0;

  private static final int MAX_PERCENT = 100;

  public BatteryLevel {
    if (percent < MIN_PERCENT || percent > MAX_PERCENT) {
      throw new IllegalArgumentException(
          "Battery level must be from %d to %d %%, was %d"
              .formatted(MIN_PERCENT, MAX_PERCENT, percent));
    }
  }

  public static BatteryLevel of(int percent) {
    return new BatteryLevel(percent);
  }
}
