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

// A guest's trip on the bike their pass holds (Reservation). It is STARTING from the request until
// telemetry confirms the unlock; it becomes IN_USE only if that happens before the unlock timeout,
// and FAILED otherwise (RIA-54, BR13). The timeout is passed in, so the rule does not depend on
// configuration.
@Entity
@Table(name = "trip")
public class Trip implements Persistable<UUID> {

  // How far the simulator's clock may be behind the server's, as for battery readings.
  static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(1);

  @Id private UUID id;

  @Column(name = "guest_id", nullable = false, updatable = false)
  private UUID guestId;

  @Column(name = "pass_id", nullable = false, updatable = false)
  private UUID passId;

  @Column(name = "reservation_id", nullable = false, updatable = false)
  private UUID reservationId;

  @Column(name = "vehicle_id", nullable = false, updatable = false)
  private UUID vehicleId;

  @Column(name = "vehicle_code", nullable = false, updatable = false, length = 32)
  private String vehicleCode;

  // The hub the bike was reserved at, where a failed start locks it again.
  @Column(name = "hub_id", updatable = false)
  private UUID hubId;

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
  @Version private long version;

  // The id is assigned in the domain, so JPA cannot tell a new trip from an existing one.
  @Transient private boolean isNew;

  protected Trip() {
    // for JPA
  }

  private Trip(Reservation reservation, UUID guestId, Instant now) {
    this.id = UUID.randomUUID();
    this.guestId = guestId;
    this.passId = reservation.getPassId();
    this.reservationId = reservation.getId();
    this.vehicleId = reservation.getVehicleId();
    this.vehicleCode = reservation.getVehicleCode();
    this.hubId = reservation.getHubId();
    this.status = TripStatus.STARTING;
    this.requestedAt = now;
    this.isNew = true;
  }

  // The trip starts on the bike its pass holds.
  public static Trip requestStart(Reservation reservation, UUID guestId, Instant now) {
    if (reservation == null || guestId == null || now == null) {
      throw new IllegalArgumentException("A trip needs a reservation, a guest and a request time");
    }
    return new Trip(reservation, guestId, now);
  }

  // Exactly at the timeout the unlock is already late: "within 30 seconds" means before 30 s.
  public boolean isUnlockOverdue(Instant now, Duration timeout) {
    return !now.isBefore(requestedAt.plus(timeout));
  }

  // What an UNLOCKED reading emitted at emittedAt, handled at now, does to this trip. A reading
  // emitted more than MAX_CLOCK_SKEW before the request was not caused by this trip's UNLOCK (a
  // redelivered QoS 1 message) and changes nothing. A late unlock fails the trip, so the bike is
  // locked again instead of starting a trip the guest was told had failed.
  public UnlockOutcome confirmUnlock(Instant emittedAt, Instant now, Duration timeout) {
    if (emittedAt.isBefore(requestedAt.minus(MAX_CLOCK_SKEW))) {
      return UnlockOutcome.STALE;
    }
    if (status == TripStatus.IN_USE) {
      return UnlockOutcome.ALREADY_STARTED;
    }
    if (status == TripStatus.FAILED) {
      return UnlockOutcome.TOO_LATE;
    }
    if (failIfUnlockOverdue(now, timeout)) {
      return UnlockOutcome.TOO_LATE;
    }
    this.status = TripStatus.IN_USE;
    this.startedAt = now;
    return UnlockOutcome.STARTED;
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

  public UUID getReservationId() {
    return reservationId;
  }

  public UUID getVehicleId() {
    return vehicleId;
  }

  public String getVehicleCode() {
    return vehicleCode;
  }

  public UUID getHubId() {
    return hubId;
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
