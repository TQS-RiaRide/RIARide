package com.riaride.fleet.internal.domain;

import com.riaride.fleet.VehicleNotBookableException;
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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Persistable;

// A vehicle of the fleet and its latest known state. The code (for example EB-601) is the id the
// simulator uses in its MQTT topics. An operator registers it at a hub (RIA-57); telemetry then
// reports its battery (RIA-59).
@Entity
@Table(name = "vehicle")
public class Vehicle implements Persistable<UUID> {

  // How far ahead of the server clock a telemetry timestamp may be (clock skew between the
  // simulator and the platform).
  static final Duration MAX_CLOCK_AHEAD = Duration.ofMinutes(1);

  public static final int MAX_CODE_LENGTH = 32;

  // The same rule as the MQTT topics (riaride/vehicles/{code}/telemetry): letters, digits and
  // hyphens only, so every registered code can appear in a topic. No spaces, slashes or wildcards.
  private static final Pattern CODE = Pattern.compile("[A-Za-z0-9-]{1," + MAX_CODE_LENGTH + "}");

  // The statuses an operator may ask for (RIA-91): activate, deactivate and retire.
  private static final Set<VehicleStatus> OPERATOR_STATUSES =
      EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE, VehicleStatus.RETIRED);

  @Id private UUID id;

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
  @Version private long version;

  // The id is assigned in the domain, so JPA cannot tell a new vehicle from an existing one.
  @Transient private boolean isNew;

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

  // A new vehicle is docked at a hub and available, with no battery reading yet. Whether the hub
  // exists and has a free slot needs the other vehicles, so RegisterVehicleService checks it.
  public static Vehicle register(String code, VehicleType type, UUID hubId) {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new InvalidVehicleException(
          "code", "A vehicle code has 1 to " + MAX_CODE_LENGTH + " letters, digits or hyphens.");
    }
    if (type == null) {
      throw new InvalidVehicleException("type", "A vehicle needs a type.");
    }
    if (hubId == null) {
      throw new InvalidVehicleException("hub", "A new vehicle must be docked at a hub.");
    }
    return new Vehicle(code, type, hubId);
  }

  // A vehicle already in a given lifecycle state, so the domain tests reach every state directly.
  static Vehicle withStatus(String code, VehicleType type, UUID hubId, VehicleStatus status) {
    var vehicle = new Vehicle(code, type, hubId);
    vehicle.status = status;
    return vehicle;
  }

  // BR12: bookable only when available and the battery is at or above the policy's minimum.
  public boolean isBookable(BookingBatteryPolicy policy) {
    return status == VehicleStatus.AVAILABLE && policy.allowsBooking(battery());
  }

  // Starting a trip reserves the vehicle first (RIA-54), so the same rule as booking applies.
  public void reserve(BookingBatteryPolicy policy) {
    if (!isBookable(policy)) {
      throw new VehicleNotBookableException();
    }
    this.status = VehicleStatus.RESERVED;
  }

  // The reservation ended without a ride (BR11): the bike can be booked again. Only a reserved bike
  // changes, so a bike an operator took out of service in the meantime stays out.
  public void release() {
    if (status == VehicleStatus.RESERVED) {
      this.status = VehicleStatus.AVAILABLE;
    }
  }

  // An operator activates (maintenance -> available), deactivates (available -> maintenance) or
  // retires (available or maintenance -> retired) the vehicle (RIA-91). Reserved and in use are
  // reached by booking and riding, so the operator never sets them, and the other transitions of
  // the lifecycle belong to bookings, trips and faults. Asking for the current status changes
  // nothing. A retired vehicle leaves the network, so it no longer takes a slot at its hub and
  // guests no longer see it there.
  public void changeStatusAsOperator(VehicleStatus target) {
    if (!OPERATOR_STATUSES.contains(target)) {
      throw new InvalidVehicleException(
          "status", "An operator can only make a vehicle available, maintenance or retired.");
    }
    if (target == status) {
      return;
    }
    if (status == VehicleStatus.RESERVED
        || status == VehicleStatus.IN_USE
        || !status.canChangeTo(target)) {
      throw new VehicleStatusChangeRefusedException(status, target);
    }
    this.status = target;
    if (target == VehicleStatus.RETIRED) {
      this.hubId = null;
    }
  }

  // Telemetry confirmed the unlock: the vehicle leaves its hub, so it no longer takes a slot.
  public void startRide() {
    if (status != VehicleStatus.RESERVED) {
      throw new IllegalStateException("Only a reserved vehicle can start a ride");
    }
    this.status = VehicleStatus.IN_USE;
    this.hubId = null;
  }

  // The trip ended at a hub with a free slot (RIA-55, BR10): the vehicle is docked there and can be
  // booked again. Whether the hub has a free slot needs the other vehicles, so
  // VehicleDockingService checks it.
  public void endRide(UUID hubId) {
    if (hubId == null) {
      throw new IllegalArgumentException("A ride ends at a hub");
    }
    if (status != VehicleStatus.IN_USE) {
      throw new IllegalStateException("Only a vehicle in use can end a ride");
    }
    this.status = VehicleStatus.AVAILABLE;
    this.hubId = hubId;
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
