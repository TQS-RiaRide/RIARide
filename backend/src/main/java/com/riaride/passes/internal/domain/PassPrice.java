package com.riaride.passes.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// BR3: one base price and deposit per pass type, for every hotel of the group (ADR 0013).
@Entity
@Table(name = "pass_price")
public class PassPrice {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "pass_type")
	private PassType passType;

	@Column(name = "price_cents", nullable = false)
	private int priceCents;

	@Column(name = "deposit_cents", nullable = false)
	private int depositCents;

	protected PassPrice() {
		// for JPA
	}

	public PassPrice(PassType passType, int priceCents, int depositCents) {
		this.passType = passType;
		change(priceCents, depositCents);
	}

	public final void change(int priceCents, int depositCents) {
		if (priceCents <= 0 || depositCents <= 0) {
			throw new IllegalArgumentException("A pass price and deposit must be positive");
		}
		this.priceCents = priceCents;
		this.depositCents = depositCents;
	}

	public PassType getPassType() {
		return passType;
	}

	public int getPriceCents() {
		return priceCents;
	}

	public int getDepositCents() {
		return depositCents;
	}

}
