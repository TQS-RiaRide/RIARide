package com.riaride.maintenance.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.maintenance.internal.domain.MaintenanceTicket;
import com.riaride.maintenance.internal.persistence.MaintenanceTicketRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Integration test (Failsafe): the ticket and the bike's new state are saved in the same
// transaction (RIA-92), with PostgreSQL in Testcontainers. When the ticket cannot be saved, the
// bike that the fleet module already moved to maintenance is rolled back to available, so a bike
// never leaves service without a ticket.
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class OpenTicketTransactionIT {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a192");

  @Autowired private OpenTicketService service;

  @Autowired private JdbcTemplate jdbc;

  @MockitoSpyBean private MaintenanceTicketRepository tickets;

  @BeforeEach
  void seedHub() {
    removeTicketsVehiclesAndHub();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Salinas – RIA-92 transaction test hub', 'TOURIST_SPOT', null, 40.64, -8.65, 5)
        """,
        HUB);
  }

  @AfterEach
  void removeTicketsVehiclesAndHub() {
    jdbc.update(
        """
        delete from maintenance_ticket
        where vehicle_id in (select id from vehicle where code like 'RIA92T-%')
        """);
    jdbc.update("delete from vehicle where code like 'RIA92T-%'");
    jdbc.update("delete from hub where id = ?", HUB);
  }

  private UUID bike(String code, String status) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', ?, ?, 80)
        """,
        id,
        code,
        status,
        "IN_USE".equals(status) ? null : HUB);
    return id;
  }

  private String storedStatus(UUID id) {
    return jdbc.queryForObject("select status from vehicle where id = ?", String.class, id);
  }

  private boolean storedPending(UUID id) {
    return jdbc.queryForObject(
        "select maintenance_pending from vehicle where id = ?", Boolean.class, id);
  }

  private int ticketCount(UUID vehicleId) {
    return jdbc.queryForObject(
        "select count(*) from maintenance_ticket where vehicle_id = ?", Integer.class, vehicleId);
  }

  @Requirement("RIA-92")
  @Test
  void shouldSaveTheTicketAndTheBikeInMaintenanceTogether() {
    var bike = bike("RIA92T-1", "AVAILABLE");

    service.open("RIA92T-1", "Rear brake does not work");

    assertThat(storedStatus(bike)).isEqualTo("MAINTENANCE");
    assertThat(ticketCount(bike)).isOne();
  }

  @Requirement("RIA-92")
  @Test
  void shouldKeepTheBikeAvailableWhenTheTicketCannotBeSaved() {
    var bike = bike("RIA92T-2", "AVAILABLE");
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(tickets)
        .save(any(MaintenanceTicket.class));

    assertThatThrownBy(() -> service.open("RIA92T-2", "Rear brake does not work"))
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(storedStatus(bike)).isEqualTo("AVAILABLE");
    assertThat(ticketCount(bike)).isZero();
  }

  @Requirement("RIA-92")
  @Test
  void shouldNotFlagABikeInUseWhenTheTicketCannotBeSaved() {
    var bike = bike("RIA92T-3", "IN_USE");
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(tickets)
        .save(any(MaintenanceTicket.class));

    assertThatThrownBy(() -> service.open("RIA92T-3", "Strange noise"))
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(storedStatus(bike)).isEqualTo("IN_USE");
    assertThat(storedPending(bike)).isFalse();
  }
}
