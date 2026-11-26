package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.passes.PassLimits;
import com.riaride.passes.PassUsage;
import com.riaride.payments.Money;
import com.riaride.payments.OvertimeCharges;
import com.riaride.payments.TransientPaymentException;
import com.riaride.trips.internal.domain.OvertimePolicy;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): once an ended trip commits, its overtime is charged off-session to the card
// saved with the pass (RIA-88, BR7). Overtime counts from the earliest of 3 h, the pass expiry
// (BR8) and the end of the stay. Each trip has its own idempotency key, so a retried charge never
// charges twice. A transient failure is thrown, so the event publication stays incomplete and is
// retried.
@ExtendWith(MockitoExtension.class)
class OvertimeChargeListenerTest {

  private static final Instant STARTED = Instant.parse("2026-11-30T10:00:00Z");
  private static final Instant FAR = Instant.parse("2026-12-03T00:00:00Z");
  private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000c0001");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0301");
  private static final UUID START_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID END_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a002");
  private static final OvertimePolicy POLICY =
      new OvertimePolicy(Duration.ofHours(3), Duration.ZERO, 200);

  @Mock private TripRepository trips;

  @Mock private PassUsage passes;

  @Mock private OvertimeCharges charges;

  private OvertimeChargeListener listener;
  private Trip trip;

  @BeforeEach
  void setUp() {
    listener = new OvertimeChargeListener(trips, passes, charges, POLICY);
    var requested = STARTED.minusSeconds(5);
    var reservation =
        Reservation.hold(
            PASS,
            new ReservedVehicle(VEHICLE, "EB-301", START_HUB),
            requested,
            Duration.ofMinutes(15));
    trip = Trip.requestStart(reservation, GUEST, requested);
    trip.confirmUnlock(requested, STARTED, Duration.ofSeconds(30));
  }

  private void givenTheTripEndedAfter(Duration length, PassLimits limits) {
    trip.end(END_HUB, STARTED.plus(length));
    when(trips.findById(trip.getId())).thenReturn(Optional.of(trip));
    when(passes.limitsOf(PASS)).thenReturn(limits);
  }

  private void tripEnded() {
    listener.on(new TripEnded(trip.getId(), "EB-301", END_HUB));
  }

  private String overtimeKey() {
    return "trip-" + trip.getId() + ":overtime";
  }

  // The acceptance criterion: 3 h 20 min is 2 blocks, at 2.00 EUR each.
  @Requirement("RIA-88")
  @Test
  void shouldCharge2BlocksOffSessionForATripOf3Hours20Minutes() {
    givenTheTripEndedAfter(Duration.ofMinutes(200), new PassLimits(FAR, FAR));

    tripEnded();

    verify(charges).charge(PASS, Money.cents(400), overtimeKey());
  }

  @Requirement("RIA-88")
  @Test
  void shouldChargeNothingForATripWithinTheMaximumDuration() {
    givenTheTripEndedAfter(Duration.ofHours(3), new PassLimits(FAR, FAR));

    tripEnded();

    verifyNoInteractions(charges);
  }

  // BR8: the pass expires 1 h into a 2 h trip, so the last hour is 4 blocks of overtime.
  @Requirement("RIA-88")
  @Test
  void shouldCountFromThePassExpiryWhenItComesBeforeTheMaximumDuration() {
    givenTheTripEndedAfter(
        Duration.ofHours(2), new PassLimits(STARTED.plus(Duration.ofHours(1)), FAR));

    tripEnded();

    verify(charges).charge(PASS, Money.cents(800), overtimeKey());
  }

  // Stripe is unreachable: the failure reaches the event registry, which keeps the publication
  // incomplete for the retry job.
  @Requirement("RIA-88")
  @Test
  void shouldLeaveTheChargeToBeRetriedWhenThePaymentProviderFailsForNow() {
    givenTheTripEndedAfter(Duration.ofMinutes(200), new PassLimits(FAR, FAR));
    var unreachable = new TransientPaymentException("api_error", "Stripe request failed", null);
    when(charges.charge(PASS, Money.cents(400), overtimeKey())).thenThrow(unreachable);

    assertThatThrownBy(this::tripEnded).isSameAs(unreachable);
  }

  // After the last attempt (OvertimeRetryJob) the overtime is recorded as unpaid, for the deposit
  // fallback (RIA-114), with the same key as the charge.
  @Requirement("RIA-88")
  @Test
  void shouldRecordTheOvertimeAsUnpaidWhenGivingUp() {
    givenTheTripEndedAfter(Duration.ofMinutes(200), new PassLimits(FAR, FAR));

    listener.giveUp(new TripEnded(trip.getId(), "EB-301", END_HUB));

    verify(charges).recordUnpaid(PASS, Money.cents(400), overtimeKey());
  }

  @Requirement("RIA-88")
  @Test
  void shouldRecordNothingWhenGivingUpOnATripWithoutOvertime() {
    givenTheTripEndedAfter(Duration.ofHours(3), new PassLimits(FAR, FAR));

    listener.giveUp(new TripEnded(trip.getId(), "EB-301", END_HUB));

    verifyNoInteractions(charges);
  }
}
