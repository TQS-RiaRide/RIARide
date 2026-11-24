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
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A vehicle of the fleet and its latest known state. The code (for example EB-601) is the id the
// simulator uses in its MQTT topics. Registration by an operator is RIA-57; this is the part that
// RIA-59 needs: the status and the battery reported by telemetry.
@Entity
@Table(name = "vehicle")
public class Vehicle implements Persistable<UUID> {

	// How far ahead of the server clock a telemetry timestamp may be (clock skew between the
	// simulator and the platform).
	static final Duration MAX_CLOCK_AHEAD = Duration.ofMinutes(1);

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

	// Telemetry updates the battery while bookings and maintenance change the status: without the
	// lock, a transaction that loaded the vehicle earlier would write its old status back.
	@Version
	private long version;

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

	// A vehicle already in a given lifecycle state. The transitions (booking, trips, maintenance)
	// belong to later stories; until then this is how the domain tests reach those states.
	static Vehicle withStatus(String code, VehicleType type, UUID hubId, VehicleStatus status) {
		var vehicle = new Vehicle(code, type, hubId);
		vehicle.status = status;
		return vehicle;
	}

	// BR12: bookable only when available and the battery is at or above the policy's minimum.
	public boolean isBookable(BookingBatteryPolicy policy) {
		return status == VehicleStatus.AVAILABLE && policy.allowsBooking(battery());
	}

	// A reading older than the last one is ignored: with QoS 1, a redelivered or late message must
	// never make a low-battery bike bookable again (BR12). A reading stamped more than
	// MAX_CLOCK_AHEAD after the server's now is ignored too: the timestamp comes from the
	// simulator's clock, and a reading from the future would otherwise hide every correct reading
	// until that time. It never changes the status.
	public void recordBattery(BatteryLevel level, Instant readAt, Instant now) {
		if (readAt.isAfter(now.plus(MAX_CLOCK_AHEAD))) {
			return;
		}
		if (lastTelemetryAt != null && readAt.isBefore(lastTelemetryAt)) {
			return;
		}
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
