package com.riaride.trips.internal.application;

import com.riaride.passes.PassUsage;
import com.riaride.payments.OvertimeCharges;
import com.riaride.trips.internal.domain.OvertimePolicy;
import com.riaride.trips.internal.persistence.TripRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

// Charges an ended trip's overtime once the end commits (RIA-88, BR7), so a payment outage never
// blocks the guest. The event publication registry keeps the publication until this listener
// succeeds: a TransientPaymentException leaves it incomplete for OvertimeRetryJob. Each trip has
// its
// own idempotency key, so a retry never charges twice.
@Component
class OvertimeChargeListener {

  private final TripRepository trips;
  private final PassUsage passes;
  private final OvertimeCharges charges;
  private final OvertimePolicy policy;

  OvertimeChargeListener(
      TripRepository trips, PassUsage passes, OvertimeCharges charges, OvertimePolicy policy) {
    this.trips = trips;
    this.passes = passes;
    this.charges = charges;
    this.policy = policy;
  }

  @ApplicationModuleListener
  void on(TripEnded ended) {
    var trip =
        trips
            .findById(ended.tripId())
            .orElseThrow(() -> new IllegalStateException("An ended trip no longer exists"));
    var limits = passes.limitsOf(trip.getPassId());
    policy
        .overtimeOf(trip.getStartedAt(), trip.getEndedAt(), limits.expiresAt(), limits.stayEndsAt())
        .amount()
        .ifPresent(amount ->
            charges.charge(trip.getPassId(), amount, "trip-" + trip.getId() + ":overtime"));
  }
}
