package com.riaride.passes.internal.domain;

import com.riaride.payments.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A guest's pass. It copies the price and deposit at purchase, so later price changes do not alter
// it (architecture, section 7). It is reserved as PENDING before the card is charged, becomes
// INACTIVE once paid, and stays inactive until the first trip (BR4). It also keeps the end of the
// guest's stay, which no pass outlasts (RIA-87).
@Entity
@Table(name = "pass")
public class Pass implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "guest_id", nullable = false, updatable = false)
  private UUID guestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "pass_type", nullable = false, updatable = false)
  private PassType passType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PassStatus status;

  @Column(name = "price_cents", nullable = false, updatable = false)
  private int priceCents;

  @Column(name = "deposit_cents", nullable = false, updatable = false)
  private int depositCents;

  @Enumerated(EnumType.STRING)
  @Column(name = "deposit_status", nullable = false)
  private DepositStatus depositStatus;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "stay_ends_at", nullable = false, updatable = false)
  private Instant stayEndsAt;

  // The id is assigned in the domain, so JPA cannot tell a new pass from an existing one.
  @Transient private boolean isNew;

  protected Pass() {
    // for JPA
  }

  private Pass(UUID guestId, PassPrice price, String purchaseKey, Instant stayEndsAt) {
    // Derived from the guest-scoped idempotency key: a retry builds the same pass, so the
    // payment provider receives the same reference and returns its cached result.
    this.id = UUID.nameUUIDFromBytes(purchaseKey.getBytes(StandardCharsets.UTF_8));
    this.guestId = guestId;
    this.passType = price.getPassType();
    this.status = PassStatus.PENDING;
    this.priceCents = price.getPriceCents();
    this.depositCents = price.getDepositCents();
    this.depositStatus = DepositStatus.HELD;
    this.stayEndsAt = stayEndsAt;
    this.isNew = true;
  }

  public static Pass purchase(
      UUID guestId, PassPrice price, String purchaseKey, Instant stayEndsAt) {
    if (guestId == null
        || price == null
        || purchaseKey == null
        || purchaseKey.isBlank()
        || stayEndsAt == null) {
      throw new IllegalArgumentException(
          "A pass needs a guest, a base price, a purchase key and the end of the stay");
    }
    return new Pass(guestId, price, purchaseKey, stayEndsAt);
  }

  // The payment went through: the pass exists, not active until the first trip (BR4).
  public void confirmPayment() {
    if (status != PassStatus.PENDING) {
      throw new IllegalStateException("Only a pending pass can be confirmed");
    }
    this.status = PassStatus.INACTIVE;
  }

  // BR4: the first trip activates the pass; later trips keep the first activation and expiry
  // times. BR2: the pass then lasts for its type's duration, but never past the end of the stay
  // (Product Owner decision, RIA-87), so a stay pass ends exactly then.
  public void activate(Instant at) {
    if (status == PassStatus.ACTIVE) {
      return;
    }
    if (status != PassStatus.INACTIVE) {
      throw new IllegalStateException("Only a paid pass can be activated");
    }
    this.status = PassStatus.ACTIVE;
    this.activatedAt = at;
    this.expiresAt =
        passType
            .duration()
            .map(at::plus)
            .filter(end -> end.isBefore(stayEndsAt))
            .orElse(stayEndsAt);
  }

  // A pass can be used for a trip once paid and until it ends: an unused pass until the end of the
  // stay (BR1), an active one until it expires.
  public boolean isUsableAt(Instant now) {
    return status == PassStatus.INACTIVE && stayEndsAt.isAfter(now)
        || status == PassStatus.ACTIVE && expiresAt.isAfter(now);
  }

  // BR8 and BR9 (RIA-89): a pass that ends during a trip stays active until that trip ends, so the
  // trip goes on and its time after the end is charged as overtime. The trip's end then closes it,
  // if it came at or after the end of the pass. The deposit stays held until its refund (RIA-114).
  // A pass that ends with no trip in progress is closed the same way (EndedPassClosingJob). True
  // only when this call closed it.
  public boolean closeIfEndedAt(Instant at) {
    if (status != PassStatus.ACTIVE || expiresAt.isAfter(at)) {
      return false;
    }
    this.status = PassStatus.CLOSED;
    return true;
  }

  // BR3 and BR5: the price and the deposit are charged together at purchase.
  public Money amountToCharge() {
    return Money.cents((long) priceCents + depositCents);
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

  public UUID getGuestId() {
    return guestId;
  }

  public PassType getPassType() {
    return passType;
  }

  public PassStatus getStatus() {
    return status;
  }

  public int getPriceCents() {
    return priceCents;
  }

  public int getDepositCents() {
    return depositCents;
  }

  public DepositStatus getDepositStatus() {
    return depositStatus;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getStayEndsAt() {
    return stayEndsAt;
  }
}
