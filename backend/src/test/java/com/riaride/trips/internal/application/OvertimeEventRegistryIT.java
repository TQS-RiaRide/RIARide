package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.telemetry.VehicleCommands;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// Integration test (Failsafe): the event publication registry, on PostgreSQL in Testcontainers,
// keeps only the overtime charge (RIA-88). Spring Modulith registers every after-commit listener by
// default; spring.modulith.events.registry-trigger-annotation limits it to
// @ApplicationModuleListener. If it did not, the bike's UNLOCK and LOCK commands would be stored
// too and could be sent again when failed publications are resubmitted.
//
// The trips module publishes these events when a trip starts, fails and ends. A publication is
// stored when its event is published and deleted once its listener succeeds (completion-mode:
// delete), so the table is read while each command is being sent, when a publication of the
// command's listener would still be there. The overtime publication is the control: it must be
// there. Its trip does not exist, so the charge fails and the publication stays for the retry job.
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class OvertimeEventRegistryIT {

  private static final String OVERTIME_LISTENER =
      "com.riaride.trips.internal.application.OvertimeChargeListener.on("
          + "com.riaride.trips.internal.application.TripEnded)";

  @Autowired private ApplicationEventPublisher events;

  @Autowired private TransactionTemplate transactions;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private VehicleCommands vehicleCommands;

  private final List<List<String>> registeredWhileUnlocking = new CopyOnWriteArrayList<>();
  private final List<List<String>> registeredWhileLocking = new CopyOnWriteArrayList<>();

  @BeforeEach
  void readTheRegistryWhileEachCommandIsSent() {
    jdbc.update("delete from event_publication");
    doAnswer(call -> registeredWhileUnlocking.add(listenerIds()))
        .when(vehicleCommands)
        .unlock(anyString());
    doAnswer(call -> registeredWhileLocking.add(listenerIds()))
        .when(vehicleCommands)
        .lock(anyString(), anyString());
  }

  @AfterEach
  void deletePublications() {
    jdbc.update("delete from event_publication");
  }

  private List<String> listenerIds() {
    return jdbc.queryForList("select listener_id from event_publication", String.class);
  }

  private void publishAndCommit(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  @Requirement("RIA-88")
  @Test
  void shouldStoreNoPublicationForTheUnlockCommand() {
    publishAndCommit(new TripStartRequested("EB-301"));

    verify(vehicleCommands).unlock("EB-301");
    assertThat(registeredWhileUnlocking).containsExactly(List.of());
    assertThat(listenerIds()).isEmpty();
  }

  @Requirement("RIA-88")
  @Test
  void shouldStoreNoPublicationForTheLockCommandOfAFailedStart() {
    publishAndCommit(new TripFailed("EB-301", UUID.randomUUID()));

    verify(vehicleCommands).lock(anyString(), anyString());
    assertThat(registeredWhileLocking).containsExactly(List.of());
    assertThat(listenerIds()).isEmpty();
  }

  // The same TripEnded event reaches the LOCK command and the overtime charge: only the charge is
  // stored, with the application's clock (FixedClockConfiguration), which OvertimeRetryJob also
  // uses to decide when a failed charge is due again.
  @Requirement("RIA-88")
  @Test
  void shouldStoreOnlyTheOvertimeChargeWhenATripEnds() {
    publishAndCommit(new TripEnded(UUID.randomUUID(), "EB-301", UUID.randomUUID()));

    verify(vehicleCommands).lock(anyString(), anyString());
    assertThat(registeredWhileLocking).containsExactly(List.of(OVERTIME_LISTENER));
    var stored = jdbc.queryForMap("select listener_id, publication_date from event_publication");
    assertThat(stored.get("listener_id")).isEqualTo(OVERTIME_LISTENER);
    assertThat(((Timestamp) stored.get("publication_date")).toInstant())
        .isEqualTo(FixedClockConfiguration.NOW);
  }
}
