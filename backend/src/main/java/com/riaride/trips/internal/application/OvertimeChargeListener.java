package com.riaride.trips.internal.application;

import com.riaride.passes.PassUsage;
import com.riaride.passes.UsageRules;
import com.riaride.payments.Money;
import com.riaride.payments.OvertimeCharges;
import com.riaride.trips.internal.domain.OvertimePolicy;
import com.riaride.trips.internal.persistence.TripRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

// Charges an ended trip's overtime once the end commits (RIA-88, BR7), so a payment outage never
// blocks the guest. The event publication registry keeps the publication until this listener
// succeeds: a TransientPaymentException leaves it failed for OvertimeRetryJob, which calls giveUp
// after the last attempt. Each trip has its own idempotency key, so a retry never charges twice.
// The listener runs outside a transaction (NOT_SUPPORTED), so the call to the payment provider
// holds no database connection; each read and write is its own short transaction.
//
// The maximum trip duration is the rule of use in force when the trip started (RIA-96), so a change
// made during the trip or before a retry never changes what the trip is charged.
@Component
class OvertimeChargeListener {

  private final TripRepository trips;
  private final PassUsage passes;
  private final OvertimeCharges charges;
  private final UsageRules rules;
  private final OvertimeProperties overtime;

  OvertimeChargeListener(
      TripRepository trips,
      PassUsage passes,
      OvertimeCharges charges,
      UsageRules rules,
      OvertimeProperties overtime) {
    this.trips = trips;
    this.passes = passes;
    this.charges = charges;
    this.rules = rules;
    this.overtime = overtime;
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(TripEnded ended) {
    overtimeOf(ended).ifPresent(due -> charges.charge(due.passId(), due.amount(), due.key()));
  }

  // The last attempt failed for now too: the overtime is recorded as unpaid, for the deposit
  // fallback (RIA-114).
  void giveUp(TripEnded ended) {
    overtimeOf(ended).ifPresent(due -> charges.recordUnpaid(due.passId(), due.amount(), due.key()));
  }

  private Optional<OvertimeDue> overtimeOf(TripEnded ended) {
    var trip =
        trips
            .findById(ended.tripId())
            .orElseThrow(() -> new IllegalStateException("An ended trip no longer exists"));
    var limits = passes.limitsOf(trip.getPassId());
    var policy =
        new OvertimePolicy(
            rules.maxTripDurationAt(trip.getStartedAt()),
            overtime.grace(),
            overtime.pricePerBlockCents());
    return policy
        .overtimeOf(trip.getStartedAt(), trip.getEndedAt(), limits.expiresAt(), limits.stayEndsAt())
        .amount()
        .map(amount ->
            new OvertimeDue(trip.getPassId(), amount, "trip-" + trip.getId() + ":overtime"));
  }

  private record OvertimeDue(UUID passId, Money amount, String key) {}
}
