package com.riaride.trips.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A guest's trip on one vehicle. It is STARTING from the request until telemetry confirms the
// unlock; it becomes IN_USE only if that happens before the unlock timeout, and FAILED otherwise
// (RIA-54). The timeout is passed in, so the rule does not depend on configuration.
@Entity
@Table(name = "trip")
public class Trip implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "guest_id", nullable = false, updatable = false)
	private UUID guestId;

	@Column(name = "pass_id", nullable = false, updatable = false)
	private UUID passId;

	@Column(name = "vehicle_id", nullable = false, updatable = false)
	private UUID vehicleId;

	@Column(name = "vehicle_code", nullable = false, updatable = false, length = 32)
	private String vehicleCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TripStatus status;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "failed_at")
	private Instant failedAt;

	// The unlock confirmation and the timeout job may update the same trip at once.
	@Version
	private long version;

	// The id is assigned in the domain, so JPA cannot tell a new trip from an existing one.
	@Transient
	private boolean isNew;

	protected Trip() {
		// for JPA
	}

	private Trip(UUID guestId, UUID passId, UUID vehicleId, String vehicleCode, Instant now) {
		this.id = UUID.randomUUID();
		this.guestId = guestId;
		this.passId = passId;
		this.vehicleId = vehicleId;
		this.vehicleCode = vehicleCode;
		this.status = TripStatus.STARTING;
		this.requestedAt = now;
		this.isNew = true;
	}

	public static Trip requestStart(UUID guestId, UUID passId, UUID vehicleId, String vehicleCode, Instant now) {
		if (guestId == null || passId == null || vehicleId == null || vehicleCode == null || now == null) {
			throw new IllegalArgumentException("A trip needs a guest, a pass, a vehicle and a request time");
		}
		return new Trip(guestId, passId, vehicleId, vehicleCode, now);
	}

	// Exactly at the timeout the unlock is already late: "within 30 seconds" means before 30 s.
	public boolean isUnlockOverdue(Instant now, Duration timeout) {
		return !now.isBefore(requestedAt.plus(timeout));
	}

	// Returns true only when this confirmation starts the trip. A repeated or late confirmation
	// changes nothing; a late one is left for failIfUnlockOverdue.
	public boolean confirmUnlock(Instant now, Duration timeout) {
		if (status != TripStatus.STARTING || isUnlockOverdue(now, timeout)) {
			return false;
		}
		this.status = TripStatus.IN_USE;
		this.startedAt = now;
		return true;
	}

	// Returns true only when this call fails the trip.
	public boolean failIfUnlockOverdue(Instant now, Duration timeout) {
		if (status != TripStatus.STARTING || !isUnlockOverdue(now, timeout)) {
			return false;
		}
		this.status = TripStatus.FAILED;
		this.failedAt = now;
		return true;
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

	public UUID getPassId() {
		return passId;
	}

	public UUID getVehicleId() {
		return vehicleId;
	}

	public String getVehicleCode() {
		return vehicleCode;
	}

	public TripStatus getStatus() {
		return status;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFailedAt() {
		return failedAt;
	}

}
