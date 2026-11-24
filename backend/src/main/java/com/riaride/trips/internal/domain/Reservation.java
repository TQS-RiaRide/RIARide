package com.riaride.trips.internal.domain;

import com.riaride.fleet.ReservedVehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A bike held for a pass (BR11). Starting a trip reserves the bike first; if the unlock is not
// confirmed in time the trip fails but the reservation stays, so the bike is still held and the
// guest can try it again until the reservation expires (BR13). An unused hold expires at expiresAt
// (ReservationExpiryJob, or the guest's next start), and its bike is released.
@Entity
@Table(name = "reservation")
public class Reservation implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "pass_id", nullable = false, updatable = false)
  private UUID passId;

  @Column(name = "vehicle_id", nullable = false, updatable = false)
  private UUID vehicleId;

  @Column(name = "vehicle_code", nullable = false, updatable = false, length = 32)
  private String vehicleCode;

  // The hub the bike was docked at when it was reserved, where a failed start locks it again.
  @Column(name = "hub_id", updatable = false)
  private UUID hubId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  // The id is assigned in the domain, so JPA cannot tell a new reservation from an existing one.
  @Transient private boolean isNew;

  protected Reservation() {
    // for JPA
  }

  private Reservation(UUID passId, ReservedVehicle vehicle, Instant now, Duration hold) {
    this.id = UUID.randomUUID();
    this.passId = passId;
    this.vehicleId = vehicle.vehicleId();
    this.vehicleCode = vehicle.code();
    this.hubId = vehicle.hubId();
    this.status = ReservationStatus.ACTIVE;
    this.createdAt = now;
    this.expiresAt = now.plus(hold);
    this.isNew = true;
  }

  public static Reservation hold(UUID passId, ReservedVehicle vehicle, Instant now, Duration hold) {
    if (passId == null || vehicle == null || now == null || hold == null) {
      throw new IllegalArgumentException("A reservation needs a pass, a bike, a time and a hold");
    }
    return new Reservation(passId, vehicle, now, hold);
  }

  // Exactly at expiresAt the hold is already over.
  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }

  // The hold ended without a trip.
  public void expire() {
    if (status != ReservationStatus.ACTIVE) {
      throw new IllegalStateException("Only an active reservation can expire");
    }
    this.status = ReservationStatus.EXPIRED;
  }

  public boolean isFor(String code) {
    return vehicleCode.equals(code);
  }

  // The trip on this bike started.
  public void markUsed() {
    if (status != ReservationStatus.ACTIVE) {
      throw new IllegalStateException("Only an active reservation can be used");
    }
    this.status = ReservationStatus.USED;
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

  public UUID getPassId() {
    return passId;
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

  public ReservationStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
