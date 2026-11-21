package com.riaride.payments.internal.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// One charge or refund at the payment provider.
@Entity
@Table(name = "payment")
class Payment implements Persistable<UUID> {

	enum Kind {
		PURCHASE
	}

	enum Status {
		SUCCEEDED
	}

	@Id
	private UUID id;

	@Column(name = "pass_id", nullable = false, updatable = false)
	private UUID passId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Kind kind;

	@Column(name = "amount_cents", nullable = false, updatable = false)
	private long amountCents;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "provider_ref", nullable = false)
	private String providerRef;

	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private String idempotencyKey;

	@Column(name = "customer_ref")
	private String customerRef;

	@Column(name = "payment_method_ref")
	private String paymentMethodRef;

	@Transient
	private boolean isNew;

	protected Payment() {
		// for JPA
	}

	private Payment(UUID passId, Kind kind, long amountCents, String providerRef, String idempotencyKey,
		String customerRef, String paymentMethodRef) {
		this.id = UUID.randomUUID();
		this.passId = passId;
		this.kind = kind;
		this.amountCents = amountCents;
		this.status = Status.SUCCEEDED;
		this.providerRef = providerRef;
		this.idempotencyKey = idempotencyKey;
		this.customerRef = customerRef;
		this.paymentMethodRef = paymentMethodRef;
		this.isNew = true;
	}

	static Payment purchase(UUID passId, long amountCents, String providerRef, String idempotencyKey,
		String customerRef, String paymentMethodRef) {
		return new Payment(passId, Kind.PURCHASE, amountCents, providerRef, idempotencyKey, customerRef,
			paymentMethodRef);
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

	UUID getPassId() {
		return passId;
	}

	Kind getKind() {
		return kind;
	}

	String getProviderRef() {
		return providerRef;
	}

}
