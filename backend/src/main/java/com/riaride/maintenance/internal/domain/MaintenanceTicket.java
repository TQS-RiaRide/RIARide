package com.riaride.maintenance.internal.domain;

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

// A ticket for a faulty bike, to be repaired before its SLA deadline (RIA-92). The deadline is the
// opening time plus the SLA, a fixed duration (24 hours), so it does not depend on the time zone.
@Entity
@Table(name = "maintenance_ticket")
public class MaintenanceTicket implements Persistable<UUID> {

  public static final int MAX_DESCRIPTION_LENGTH = 500;

  @Id private UUID id;

  @Column(name = "vehicle_id", nullable = false, updatable = false)
  private UUID vehicleId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private TicketSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TicketStatus status;

  @Column(nullable = false, updatable = false, length = MAX_DESCRIPTION_LENGTH)
  private String description;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private Instant openedAt;

  @Column(name = "sla_due_at", nullable = false, updatable = false)
  private Instant slaDueAt;

  // The id is assigned in the domain, so JPA cannot tell a new ticket from an existing one.
  @Transient private boolean isNew;

  protected MaintenanceTicket() {
    // for JPA
  }

  private MaintenanceTicket(UUID vehicleId, String description, Instant now, Duration sla) {
    this.id = UUID.randomUUID();
    this.vehicleId = vehicleId;
    this.source = TicketSource.OPERATOR;
    this.status = TicketStatus.OPEN;
    this.description = description;
    this.openedAt = now;
    this.slaDueAt = now.plus(sla);
    this.isNew = true;
  }

  // An operator reports a fault on a bike. Whether the bike can be taken out of service is the
  // fleet module's rule, checked in the same transaction (OpenTicketService).
  public static MaintenanceTicket openByOperator(
      UUID vehicleId, String description, Instant now, Duration sla) {
    if (vehicleId == null || now == null || sla == null) {
      throw new IllegalArgumentException("A ticket needs a bike, an opening time and an SLA");
    }
    return new MaintenanceTicket(vehicleId, validDescription(description), now, sla);
  }

  private static String validDescription(String description) {
    var trimmed = description == null ? "" : description.strip();
    if (trimmed.isEmpty() || trimmed.length() > MAX_DESCRIPTION_LENGTH) {
      throw new InvalidTicketException(
          "description", "A fault description has 1 to " + MAX_DESCRIPTION_LENGTH + " characters.");
    }
    return trimmed;
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

  public UUID getVehicleId() {
    return vehicleId;
  }

  public TicketSource getSource() {
    return source;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public String getDescription() {
    return description;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getSlaDueAt() {
    return slaDueAt;
  }
}
