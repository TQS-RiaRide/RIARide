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
// INACTIVE once paid, and stays inactive until the first trip (BR4).
@Entity
@Table(name = "pass")
public class Pass implements Persistable<UUID> {

	@Id
	private UUID id;

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

	// The id is assigned in the domain, so JPA cannot tell a new pass from an existing one.
	@Transient
	private boolean isNew;

	protected Pass() {
		// for JPA
	}

	private Pass(UUID guestId, PassPrice price, String purchaseKey) {
		// Derived from the guest-scoped idempotency key: a retry builds the same pass, so the
		// payment provider receives the same reference and returns its cached result.
		this.id = UUID.nameUUIDFromBytes(purchaseKey.getBytes(StandardCharsets.UTF_8));
		this.guestId = guestId;
		this.passType = price.getPassType();
		this.status = PassStatus.PENDING;
		this.priceCents = price.getPriceCents();
		this.depositCents = price.getDepositCents();
		this.depositStatus = DepositStatus.HELD;
		this.isNew = true;
	}

	public static Pass purchase(UUID guestId, PassPrice price, String purchaseKey) {
		if (guestId == null || price == null || purchaseKey == null || purchaseKey.isBlank()) {
			throw new IllegalArgumentException("A pass needs a guest, a base price and a purchase key");
		}
		return new Pass(guestId, price, purchaseKey);
	}

	// The payment went through: the pass exists, not active until the first trip (BR4).
	public void confirmPayment() {
		if (status != PassStatus.PENDING) {
			throw new IllegalStateException("Only a pending pass can be confirmed");
		}
		this.status = PassStatus.INACTIVE;
	}

	// BR4: the first trip activates the pass; later trips keep the first activation time. Setting
	// expiresAt per pass type belongs to the pass-expiry story.
	public void activate(Instant at) {
		if (status == PassStatus.ACTIVE) {
			return;
		}
		if (status != PassStatus.INACTIVE) {
			throw new IllegalStateException("Only a paid pass can be activated");
		}
		this.status = PassStatus.ACTIVE;
		this.activatedAt = at;
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

}
