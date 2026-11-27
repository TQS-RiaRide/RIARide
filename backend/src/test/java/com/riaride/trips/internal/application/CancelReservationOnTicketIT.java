package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.fleet.VehicleNotBookableException;
import com.riaride.maintenance.internal.application.OpenTicketService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): an operator opens a ticket for a reserved bike (RIA-92), with
// PostgreSQL in Testcontainers. Once the ticket's transaction commits, the bike is in maintenance
// and its active reservation is cancelled, so the guest's pass can hold another bike. Until then,
// a retry of the guest's failed start does not reuse the hold of a bike in maintenance.
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class CancelReservationOnTicketIT {

  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

  @Autowired private OpenTicketService openTicket;

  @Autowired private StartTripService startTrip;

  @Autowired private JdbcTemplate jdbc;

  private String keycloakId;

  private UUID vehicleId;
  private UUID reservationId;

  @BeforeEach
  void givenAReservedBike() {
    cleanUp();
    var hubId = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Marés Centro – RIA-92 reservation test hub', 'HOTEL', ?::uuid, 40.64, -8.65, 5)
        """,
        hubId,
        MARES_CENTRO);
    var stayId = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Rita Almeida', 'MNTA2345', date '2026-11-28', date '2026-12-02',
                'ACTIVE')
        """,
        stayId,
        MARES_CENTRO);
    var guestId = UUID.randomUUID();
    jdbc.update(
        """
        insert into guest (id, keycloak_id, stay_id, email, locale)
        values (?, ?, ?, 'rita.almeida@example.test', 'pt')
        """,
        guestId,
        keycloakId = UUID.randomUUID().toString(),
        stayId);
    var passId = UUID.randomUUID();
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents,
                          deposit_status)
        values (?, ?, 'STAY', 'ACTIVE', 4000, 5000, 'HELD')
        """,
        passId,
        guestId);
    vehicleId = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, 'RIA92R-1', 'E_BIKE', 'RESERVED', ?, 80)
        """,
        vehicleId,
        hubId);
    reservationId = UUID.randomUUID();
    jdbc.update(
        """
        insert into reservation (id, pass_id, vehicle_id, vehicle_code, hub_id, status,
                                 created_at, expires_at)
        values (?, ?, ?, 'RIA92R-1', ?, 'ACTIVE', timestamptz '2026-11-30T09:55:00Z',
                timestamptz '2026-11-30T10:10:00Z')
        """,
        reservationId,
        passId,
        vehicleId,
        hubId);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update(
        """
        delete from maintenance_ticket
        where vehicle_id in (select id from vehicle where code like 'RIA92R-%')
        """);
    jdbc.update("delete from trip where vehicle_code like 'RIA92R-%'");
    jdbc.update("delete from reservation where vehicle_code like 'RIA92R-%'");
    jdbc.update("delete from vehicle where code like 'RIA92R-%'");
    jdbc.update("delete from hub where name like '%RIA-92 reservation test hub'");
    jdbc.update(
        "delete from pass where guest_id in (select id from guest where email = ?)",
        "rita.almeida@example.test");
    jdbc.update("delete from guest where email = ?", "rita.almeida@example.test");
    jdbc.update("delete from stay where stay_code = 'MNTA2345'");
  }

  @Requirement("RIA-92")
  @Test
  void shouldMoveTheReservedBikeToMaintenanceAndCancelItsReservation() {
    openTicket.open("RIA92R-1", "Front light is broken");

    assertThat(
            jdbc.queryForObject("select status from vehicle where id = ?", String.class, vehicleId))
        .isEqualTo("MAINTENANCE");
    assertThat(
            jdbc.queryForObject(
                "select status from reservation where id = ?", String.class, reservationId))
        .isEqualTo("CANCELLED");
  }

  // The ticket committed but its reservation was not cancelled yet (the listener has not run, or
  // failed): the guest retries a failed start on the bike, and no trip or UNLOCK follows.
  @Requirement("RIA-92")
  @Test
  void shouldNotStartATripOnAHeldBikeThatIsInMaintenance() {
    jdbc.update("update vehicle set status = 'MAINTENANCE' where id = ?", vehicleId);

    assertThatThrownBy(() -> startTrip.start(new StartTripCommand(keycloakId, "RIA92R-1")))
        .isInstanceOf(VehicleNotBookableException.class);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from trip where reservation_id = ?", Integer.class, reservationId))
        .isZero();
  }
}
