package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.fleet.VehicleReservations;
import com.riaride.telemetry.VehicleTelemetryReceived;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// Integration test (Failsafe): a battery reading and a trip start on the same bike at the same time
// (RIA-54). The reading's update writes the whole vehicle row, so without a row lock it would put
// back the status it read and undo the reservation that committed in between: the trip would then
// be starting on a bike listed as available. The telemetry listener locks the row, so the start
// waits and reserves the bike with its new battery.
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class VehicleTelemetryLockingIT {

  @Autowired private VehicleTelemetryListener telemetry;

  @Autowired private VehicleReservations reservations;

  @Autowired private TransactionTemplate transactions;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void bikeAtAHub() {
    var hubId = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Marés Centro', 'HOTEL', '00000000-0000-0000-0000-000000000001'::uuid, 40.6405, -8.6538, 10)
        """,
        hubId);
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, 'EB-201', 'E_BIKE', 'AVAILABLE', ?, 80)
        """,
        UUID.randomUUID(),
        hubId);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from vehicle");
    jdbc.update("delete from hub");
  }

  @Requirement("RIA-54")
  @Test
  void shouldKeepAReservationMadeWhileABatteryReadingIsStored() throws Exception {
    var readingLoaded = new CountDownLatch(1);
    var reserved = new CountDownLatch(1);
    var reading =
        new VehicleTelemetryReceived(
            "EB-201",
            UUID.randomUUID().toString(),
            FixedClockConfiguration.NOW,
            70,
            null,
            "LOCKED");

    var storeReading =
        CompletableFuture.runAsync(() ->
            transactions.executeWithoutResult(status -> {
              telemetry.on(reading);
              readingLoaded.countDown();
              // Gives the start the chance to commit before this reading does. With the row lock
              // the
              // start cannot get the bike yet, so this wait ends by its timeout.
              await(reserved);
            }));
    var startTrip =
        CompletableFuture.runAsync(() -> {
          await(readingLoaded);
          transactions.executeWithoutResult(status -> reservations.reserve("EB-201"));
          reserved.countDown();
        });
    CompletableFuture.allOf(storeReading, startTrip).get(10, TimeUnit.SECONDS);

    var bike = jdbc.queryForMap("select status, battery_pct from vehicle where code = 'EB-201'");
    assertThat(bike.get("status")).isEqualTo("RESERVED");
    assertThat(bike.get("battery_pct")).isEqualTo(70);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(1, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
