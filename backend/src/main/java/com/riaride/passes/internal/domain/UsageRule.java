package com.riaride.passes.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// The rules of use in force from a moment on (RIA-96). A change never edits a rule: it adds one
// from the moment it is made, so the rule a trip started under can still be read when its overtime
// is charged, or charged again after a failure (RIA-88). For now the rule holds the maximum trip
// duration (BR7); the other thresholds stay in configuration (architecture, section 7).
@Entity
@Table(name = "usage_rule")
public class UsageRule implements Persistable<UUID> {

  // A quarter of an hour, the overtime block, up to a whole day.
  public static final int MIN_MAX_TRIP_DURATION_MINUTES = 15;
  public static final int MAX_MAX_TRIP_DURATION_MINUTES = 24 * 60;

  @Id private UUID id;

  @Column(name = "max_trip_duration_minutes", nullable = false, updatable = false)
  private int maxTripDurationMinutes;

  @Column(name = "effective_from", nullable = false, updatable = false)
  private Instant effectiveFrom;

  // The id is assigned in the domain, so JPA cannot tell a new rule from an existing one.
  @Transient private boolean isNew;

  protected UsageRule() {
    // for JPA
  }

  private UsageRule(int maxTripDurationMinutes, Instant effectiveFrom) {
    this.id = UUID.randomUUID();
    this.maxTripDurationMinutes = maxTripDurationMinutes;
    this.effectiveFrom = effectiveFrom;
    this.isNew = true;
  }

  public static UsageRule from(Instant effectiveFrom, int maxTripDurationMinutes) {
    if (effectiveFrom == null) {
      throw new IllegalArgumentException("A rule of use applies from a moment");
    }
    if (maxTripDurationMinutes < MIN_MAX_TRIP_DURATION_MINUTES
        || maxTripDurationMinutes > MAX_MAX_TRIP_DURATION_MINUTES) {
      throw new InvalidUsageRuleException(
          "maxTripDurationMinutes",
          "The maximum trip duration must be from "
              + MIN_MAX_TRIP_DURATION_MINUTES
              + " minutes to "
              + MAX_MAX_TRIP_DURATION_MINUTES / 60
              + " hours.");
    }
    return new UsageRule(maxTripDurationMinutes, effectiveFrom);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  public int getMaxTripDurationMinutes() {
    return maxTripDurationMinutes;
  }

  public Duration maxTripDuration() {
    return Duration.ofMinutes(maxTripDurationMinutes);
  }

  public Instant getEffectiveFrom() {
    return effectiveFrom;
  }
}
