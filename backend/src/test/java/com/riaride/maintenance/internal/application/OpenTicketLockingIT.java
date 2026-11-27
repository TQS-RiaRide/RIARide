package com.riaride.maintenance.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.fleet.FaultHandling;
import com.riaride.fleet.VehicleNotBookableException;
import com.riaride.fleet.VehicleReservations;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// Integration test (Failsafe): a ticket and a booking for the same bike at the same time (RIA-92),
// with PostgreSQL in Testcontainers. The vehicle row lock makes one wait for the other, so the bike
// always ends in maintenance: a booking that comes first is seen by the ticket (and its
// reservation is cancelled), and a booking that comes second is refused. The second transaction
// is held until pg_locks shows it waiting, so there is no fixed sleep.
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class OpenTicketLockingIT {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a292");

  @Autowired private OpenTicketService openTicket;

  @Autowired private VehicleReservations reservations;

  @Autowired private TransactionTemplate transactions;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void bikeAtAHub() {
    cleanUp();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Praia – RIA-92 locking test hub', 'TOURIST_SPOT', null, 40.64, -8.75, 5)
        """,
        HUB);
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, 'RIA92L-1', 'E_BIKE', 'AVAILABLE', ?, 80)
        """,
        UUID.randomUUID(),
        HUB);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update(
        """
        delete from maintenance_ticket
        where vehicle_id in (select id from vehicle where code like 'RIA92L-%')
        """);
    jdbc.update("delete from vehicle where code like 'RIA92L-%'");
    jdbc.update("delete from hub where id = ?", HUB);
  }

  @Requirement("RIA-92")
  @Test
  void shouldSeeABookingThatHoldsTheBikeAndTakeTheBikeOutOfServiceAfterIt() throws Exception {
    var reserved = new CountDownLatch(1);
    var opened = new CountDownLatch(1);

    var book =
        CompletableFuture.runAsync(() ->
            transactions.executeWithoutResult(status -> {
              reservations.reserve("RIA92L-1");
              reserved.countDown();
              await()
                  .atMost(Duration.ofSeconds(5))
                  .until(() -> opened.getCount() == 0 || aTransactionWaitsForALock());
            }));
    var ticket =
        CompletableFuture.supplyAsync(() -> {
          awaitLatch(reserved);
          var result = openTicket.open("RIA92L-1", "Saddle is broken");
          opened.countDown();
          return result;
        });
    CompletableFuture.allOf(book, ticket).get(10, TimeUnit.SECONDS);

    assertThat(ticket.get().handling()).isEqualTo(FaultHandling.OUT_OF_SERVICE_WAS_RESERVED);
    assertThat(storedStatus()).isEqualTo("MAINTENANCE");
    assertThat(ticketCount()).isOne();
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseABookingThatWaitedForTheTicket() throws Exception {
    var ticketLoaded = new CountDownLatch(1);
    var booked = new CountDownLatch(1);

    var ticket =
        CompletableFuture.runAsync(() ->
            transactions.executeWithoutResult(status -> {
              openTicket.open("RIA92L-1", "Saddle is broken");
              ticketLoaded.countDown();
              await()
                  .atMost(Duration.ofSeconds(5))
                  .until(() -> booked.getCount() == 0 || aTransactionWaitsForALock());
            }));
    var book =
        CompletableFuture.runAsync(() -> {
          awaitLatch(ticketLoaded);
          try {
            transactions.executeWithoutResult(status -> reservations.reserve("RIA92L-1"));
          } finally {
            booked.countDown();
          }
        });

    ticket.get(10, TimeUnit.SECONDS);
    assertThatThrownBy(() -> book.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(VehicleNotBookableException.class);
    assertThat(storedStatus()).isEqualTo("MAINTENANCE");
    assertThat(ticketCount()).isOne();
  }

  private String storedStatus() {
    return jdbc.queryForObject("select status from vehicle where code = 'RIA92L-1'", String.class);
  }

  private int ticketCount() {
    return jdbc.queryForObject(
        """
        select count(*) from maintenance_ticket t join vehicle v on v.id = t.vehicle_id
        where v.code = 'RIA92L-1'
        """,
        Integer.class);
  }

  private boolean aTransactionWaitsForALock() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists (select 1 from pg_locks where not granted)", Boolean.class));
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
