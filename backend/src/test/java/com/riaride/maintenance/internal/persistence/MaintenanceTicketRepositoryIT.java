package com.riaride.maintenance.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestRows;
import com.riaride.TestcontainersConfiguration;
import com.riaride.maintenance.internal.domain.MaintenanceTicket;
import com.riaride.maintenance.internal.domain.TicketSource;
import com.riaride.maintenance.internal.domain.TicketStatus;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the maintenance_ticket table (Flyway V11) and its constraints on
// PostgreSQL in Testcontainers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MaintenanceTicketRepositoryIT {

  private static final Instant OPENED_AT = Instant.parse("2026-11-30T10:00:00Z");
  private static final Duration SLA = Duration.ofHours(24);

  @Autowired private MaintenanceTicketRepository tickets;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private EntityManager entityManager;

  private UUID vehicleId;

  @BeforeEach
  void bikeAtAHub() {
    var hubId = TestRows.touristSpot(jdbc, "Miradouro da Ria", 6);
    vehicleId = TestRows.vehicle(jdbc, "EB-701", "MAINTENANCE", hubId);
  }

  @Requirement("RIA-92")
  @Test
  void shouldStoreATicketWithItsSlaDeadline() {
    var opened =
        tickets.saveAndFlush(
            MaintenanceTicket.openByOperator(
                vehicleId, "  Rear brake does not grip  ", OPENED_AT, SLA));
    entityManager.clear();

    assertThat(tickets.findById(opened.getId()))
        .get()
        .satisfies(ticket -> {
          assertThat(ticket.getVehicleId()).isEqualTo(vehicleId);
          assertThat(ticket.getSource()).isEqualTo(TicketSource.OPERATOR);
          assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
          assertThat(ticket.getDescription()).isEqualTo("Rear brake does not grip");
          assertThat(ticket.getOpenedAt()).isEqualTo(OPENED_AT);
          assertThat(ticket.getSlaDueAt()).isEqualTo(OPENED_AT.plus(SLA));
        });
  }

  @Requirement("RIA-92")
  @Test
  void shouldRejectATicketForABikeThatDoesNotExist() {
    var ticket = MaintenanceTicket.openByOperator(UUID.randomUUID(), "Flat tyre", OPENED_AT, SLA);

    assertThatThrownBy(() -> tickets.saveAndFlush(ticket))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // The deadline comes after the opening time even if the domain is bypassed.
  @Requirement("RIA-92")
  @Test
  void shouldRejectADeadlineThatIsNotAfterTheOpeningInTheDatabase() {
    var ticket =
        tickets.saveAndFlush(
            MaintenanceTicket.openByOperator(vehicleId, "Flat tyre", OPENED_AT, SLA));

    assertThatThrownBy(() ->
            jdbc.update(
                "update maintenance_ticket set sla_due_at = opened_at where id = ?",
                ticket.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Requirement("RIA-92")
  @Test
  void shouldRejectABlankDescriptionInTheDatabase() {
    var ticket =
        tickets.saveAndFlush(
            MaintenanceTicket.openByOperator(vehicleId, "Flat tyre", OPENED_AT, SLA));

    assertThatThrownBy(() ->
            jdbc.update(
                "update maintenance_ticket set description = '   ' where id = ?", ticket.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
