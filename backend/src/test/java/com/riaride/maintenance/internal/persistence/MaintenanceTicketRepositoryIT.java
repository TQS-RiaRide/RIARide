package com.riaride.maintenance.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.maintenance.internal.domain.MaintenanceTicket;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the maintenance_ticket table (Flyway V12) on PostgreSQL in
// Testcontainers. A ticket read back from the database keeps its bike, fault, status and SLA
// deadline (RIA-92), and is not saved again as a new row.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MaintenanceTicketRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
  private static final Duration SLA = Duration.ofHours(24);
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

  @Autowired private MaintenanceTicketRepository tickets;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private TestEntityManager entityManager;

  private UUID vehicleId;

  @BeforeEach
  void givenABike() {
    var hubId = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Marés Centro – RIA-92 ticket repository hub', 'HOTEL', ?::uuid, 40.64, -8.65, 5)
        """,
        hubId,
        MARES_CENTRO);
    vehicleId = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, 'RIA92T-1', 'E_BIKE', 'MAINTENANCE', ?, 80)
        """,
        vehicleId,
        hubId);
  }

  @Requirement("RIA-92")
  @Test
  void shouldReadBackAnOpenedTicketWithItsDeadline() {
    var opened = MaintenanceTicket.openByOperator(vehicleId, "Rear brake does not work", NOW, SLA);
    tickets.saveAndFlush(opened);
    entityManager.clear();

    var read = tickets.findById(opened.getId()).orElseThrow();

    assertThat(read).isNotSameAs(opened);
    assertThat(read.isNew()).isFalse();
    assertThat(read.getVehicleId()).isEqualTo(vehicleId);
    assertThat(read.getSource()).isEqualTo(opened.getSource());
    assertThat(read.getStatus()).isEqualTo(opened.getStatus());
    assertThat(read.getDescription()).isEqualTo("Rear brake does not work");
    assertThat(read.getOpenedAt()).isEqualTo(NOW);
    assertThat(read.getSlaDueAt()).isEqualTo(NOW.plus(SLA));
  }
}
