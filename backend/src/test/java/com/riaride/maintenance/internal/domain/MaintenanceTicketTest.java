package com.riaride.maintenance.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): an operator opens a maintenance ticket for a faulty bike, and the ticket's
// SLA deadline is the opening time plus the SLA (24 hours, RIA-92).
class MaintenanceTicketTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final Duration SLA = Duration.ofHours(24);
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0901");

  @Requirement("RIA-92")
  @Test
  void shouldOpenTicketWithDeadline24HoursAfterOpening() {
    var ticket = MaintenanceTicket.openByOperator(VEHICLE, "Rear brake does not work", NOW, SLA);

    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    assertThat(ticket.getSource()).isEqualTo(TicketSource.OPERATOR);
    assertThat(ticket.getVehicleId()).isEqualTo(VEHICLE);
    assertThat(ticket.getOpenedAt()).isEqualTo(NOW);
    assertThat(ticket.getSlaDueAt()).isEqualTo(Instant.parse("2026-12-01T10:00:00Z"));
  }

  // The deadline is a fixed duration, not "the same time tomorrow": a ticket opened late in the
  // evening is due the next evening, and a ticket opened at 23:59:59 one second before midnight.
  @Requirement("RIA-92")
  @Test
  void shouldCountTheDeadlineFromTheExactOpeningTime() {
    var openedAt = Instant.parse("2026-11-30T23:59:59Z");

    var ticket = MaintenanceTicket.openByOperator(VEHICLE, "Flat tyre", openedAt, SLA);

    assertThat(ticket.getSlaDueAt()).isEqualTo(Instant.parse("2026-12-01T23:59:59Z"));
  }

  @Requirement("RIA-92")
  @Test
  void shouldGiveEachTicketItsOwnId() {
    var first = MaintenanceTicket.openByOperator(VEHICLE, "Flat tyre", NOW, SLA);
    var second = MaintenanceTicket.openByOperator(VEHICLE, "Flat tyre", NOW, SLA);

    assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
    assertThat(first.isNew()).isTrue();
  }

  @Requirement("RIA-92")
  @Test
  void shouldTrimTheFaultDescription() {
    var ticket = MaintenanceTicket.openByOperator(VEHICLE, "  Chain is loose \n", NOW, SLA);

    assertThat(ticket.getDescription()).isEqualTo("Chain is loose");
  }

  @Requirement("RIA-92")
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\n\t"})
  void shouldRefuseTicketWithoutAFaultDescription(String description) {
    assertThatThrownBy(() -> MaintenanceTicket.openByOperator(VEHICLE, description, NOW, SLA))
        .isInstanceOf(InvalidTicketException.class)
        .extracting("field")
        .isEqualTo("description");
  }

  // Boundary: 500 characters is the longest description.
  @Requirement("RIA-92")
  @Test
  void shouldAcceptDescriptionOfExactly500Characters() {
    var description = "x".repeat(MaintenanceTicket.MAX_DESCRIPTION_LENGTH);

    var ticket = MaintenanceTicket.openByOperator(VEHICLE, description, NOW, SLA);

    assertThat(ticket.getDescription()).hasSize(500);
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseDescriptionOf501Characters() {
    var description = "x".repeat(MaintenanceTicket.MAX_DESCRIPTION_LENGTH + 1);

    assertThatThrownBy(() -> MaintenanceTicket.openByOperator(VEHICLE, description, NOW, SLA))
        .isInstanceOf(InvalidTicketException.class)
        .extracting("field")
        .isEqualTo("description");
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseTicketWithoutABikeTimeOrSla() {
    assertThatThrownBy(() -> MaintenanceTicket.openByOperator(null, "Flat tyre", NOW, SLA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MaintenanceTicket.openByOperator(VEHICLE, "Flat tyre", null, SLA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MaintenanceTicket.openByOperator(VEHICLE, "Flat tyre", NOW, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
