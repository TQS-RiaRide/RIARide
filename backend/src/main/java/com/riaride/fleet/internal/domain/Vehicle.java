package com.riaride.fleet.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A vehicle of the fleet and its latest known state. The code (for example EB-601) is the id the
// simulator uses in its MQTT topics. Registration by an operator is RIA-57; this is the part that
// RIA-59 needs: the status and the battery reported by telemetry.
@Entity
@Table(name = "vehicle")
public class Vehicle implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false, length = 32)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private VehicleType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VehicleStatus status;

	@Column(name = "hub_id")
	private UUID hubId;

	// Null until the first telemetry reading arrives.
	@Column(name = "battery_pct")
	private Integer batteryPct;

	@Column(name = "last_telemetry_at")
	private Instant lastTelemetryAt;

	// The id is assigned in the domain, so JPA cannot tell a new vehicle from an existing one.
	@Transient
	private boolean isNew;

	protected Vehicle() {
		// for JPA
	}

	private Vehicle(String code, VehicleType type, UUID hubId) {
		this.id = UUID.randomUUID();
		this.code = code;
		this.type = type;
		this.status = VehicleStatus.AVAILABLE;
		this.hubId = hubId;
		this.isNew = true;
	}

	// A new vehicle is docked at a hub and available, with no battery reading yet.
	public static Vehicle register(String code, VehicleType type, UUID hubId) {
		return new Vehicle(code, type, hubId);
	}

	// BR12: bookable only when available and the battery is at or above the policy's minimum.
	public boolean isBookable(BookingBatteryPolicy policy) {
		return status == VehicleStatus.AVAILABLE && policy.allowsBooking(battery());
	}

	public void recordBattery(BatteryLevel level, Instant readAt) {
		this.batteryPct = level.percent();
		this.lastTelemetryAt = readAt;
	}

	public BatteryLevel battery() {
		return batteryPct == null ? null : BatteryLevel.of(batteryPct);
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

	public String getCode() {
		return code;
	}

	public VehicleType getType() {
		return type;
	}

	public VehicleStatus getStatus() {
		return status;
	}

	public UUID getHubId() {
		return hubId;
	}

	public Instant getLastTelemetryAt() {
		return lastTelemetryAt;
	}

}
