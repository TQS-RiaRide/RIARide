package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a pass is bought at the base price of its type (BR3), with the deposit
// (BR5), and is not active until the first trip (BR4).
class PassTest {

  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
  private static final PassPrice STAY_PRICE = new PassPrice(PassType.STAY, 2500, 5000);
  private static final String KEY = GUEST + ":purchase-1";
  // Check-out on 2 December: the stay ends at midnight in Vila Ria (RIA-87).
  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");

  // The pass is reserved (PENDING) before the card is charged, so the one-open-pass index guards
  // concurrent purchases before any money moves.
  @Requirement("RIA-52")
  @Test
  void shouldStartAsAPendingReservation() {
    assertThat(Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS).getStatus())
        .isEqualTo(PassStatus.PENDING);
  }

  @Requirement("RIA-52")
  @Test
  void shouldBecomeAPassThatIsNotActiveYetOnceItIsPaid() {
    var pass = Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS);

    pass.confirmPayment();

    assertThat(pass.getStatus()).isEqualTo(PassStatus.INACTIVE);
    assertThat(pass.getActivatedAt()).isNull();
    assertThat(pass.getExpiresAt()).isNull();
  }

  @Requirement("RIA-52")
  @Test
  void shouldCopyPriceAndDepositOfItsType() {
    var pass = Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS);

    assertThat(pass.getGuestId()).isEqualTo(GUEST);
    assertThat(pass.getPassType()).isEqualTo(PassType.STAY);
    assertThat(pass.getPriceCents()).isEqualTo(2500);
    assertThat(pass.getDepositCents()).isEqualTo(5000);
    assertThat(pass.getDepositStatus()).isEqualTo(DepositStatus.HELD);
  }

  @Requirement("RIA-52")
  @Test
  void shouldChargePriceAndDepositTogether() {
    var pass = Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS);

    assertThat(pass.amountToCharge().cents()).isEqualTo(7500);
  }

  // The pass keeps the amounts it was sold at, so a later price change does not alter it.
  @Requirement("RIA-52")
  @Test
  void shouldKeepItsAmountsWhenTheBasePriceChangesLater() {
    var price = new PassPrice(PassType.ONE_DAY, 1000, 5000);
    var pass = Pass.purchase(GUEST, price, KEY, STAY_ENDS);

    price.change(1200, 6000);

    assertThat(pass.getPriceCents()).isEqualTo(1000);
    assertThat(pass.getDepositCents()).isEqualTo(5000);
  }

  // The id comes from the guest-scoped idempotency key, so a retry sends Stripe the same reference
  // and gets its cached result instead of a parameter mismatch.
  @Requirement("RIA-52")
  @Test
  void shouldDeriveTheSameIdFromTheSameKey() {
    assertThat(Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS).getId())
        .isEqualTo(Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS).getId());
  }

  @Requirement("RIA-52")
  @Test
  void shouldDeriveDifferentIdsFromDifferentKeys() {
    assertThat(Pass.purchase(GUEST, STAY_PRICE, KEY, STAY_ENDS).getId())
        .isNotEqualTo(Pass.purchase(GUEST, STAY_PRICE, GUEST + ":purchase-2", STAY_ENDS).getId());
  }

  @Requirement("RIA-52")
  @Test
  void shouldRequireAnIdempotencyKey() {
    assertThatThrownBy(() -> Pass.purchase(GUEST, STAY_PRICE, " ", STAY_ENDS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRequireAGuest() {
    assertThatThrownBy(() -> Pass.purchase(null, STAY_PRICE, KEY, STAY_ENDS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // A pass cannot end later than the guest's stay, so it must know when the stay ends (RIA-87).
  @Requirement("RIA-87")
  @Test
  void shouldRequireTheEndOfTheStay() {
    assertThatThrownBy(() -> Pass.purchase(GUEST, STAY_PRICE, KEY, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldBeOpenUntilClosed() {
    assertThat(PassStatus.PENDING.isOpen()).isTrue();
    assertThat(PassStatus.INACTIVE.isOpen()).isTrue();
    assertThat(PassStatus.ACTIVE.isOpen()).isTrue();
    assertThat(PassStatus.CLOSED.isOpen()).isFalse();
  }
}
