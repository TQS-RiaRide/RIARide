package com.riaride.maintenance.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.FaultHandling;
import com.riaride.fleet.FaultyVehicle;
import com.riaride.fleet.VehicleMaintenance;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.VehicleRetiredException;
import com.riaride.maintenance.MaintenanceTicketOpened;
import com.riaride.maintenance.internal.domain.InvalidTicketException;
import com.riaride.maintenance.internal.domain.MaintenanceTicket;
import com.riaride.maintenance.internal.domain.TicketStatus;
import com.riaride.maintenance.internal.persistence.MaintenanceTicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

// Unit test (Surefire): an operator opens a maintenance ticket for a faulty bike (RIA-92). The
// ticket is due 24 hours after the injected clock's now; the bike leaves service through the fleet
// API in the same transaction (OpenTicketTransactionIT proves it), and every opened ticket is
// announced so that the trips module can cancel the bike's reservation.
@ExtendWith(MockitoExtension.class)
class OpenTicketServiceTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final Duration SLA = Duration.ofHours(24);
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0901");

  @Mock private VehicleMaintenance fleet;

  @Mock private MaintenanceTicketRepository tickets;

  @Mock private ApplicationEventPublisher events;

  private OpenTicketService service;

  @BeforeEach
  void setUp() {
    service =
        new OpenTicketService(
            fleet,
            tickets,
            events,
            new MaintenanceProperties(SLA),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void givenFaultHandled(FaultHandling handling) {
    when(fleet.reportFault("EB-901")).thenReturn(new FaultyVehicle(VEHICLE, "EB-901", handling));
    when(tickets.save(any(MaintenanceTicket.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Requirement("RIA-92")
  @Test
  void shouldOpenTicketDueTwentyFourHoursAfterNow() {
    givenFaultHandled(FaultHandling.OUT_OF_SERVICE);

    var opened = service.open("EB-901", "Rear brake does not work");

    var ticket = opened.ticket();
    assertThat(ticket.getVehicleId()).isEqualTo(VEHICLE);
    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    assertThat(ticket.getOpenedAt()).isEqualTo(NOW);
    assertThat(ticket.getSlaDueAt()).isEqualTo(Instant.parse("2026-12-01T10:00:00Z"));
    assertThat(opened.vehicleCode()).isEqualTo("EB-901");
    assertThat(opened.handling()).isEqualTo(FaultHandling.OUT_OF_SERVICE);
    verify(tickets).save(ticket);
  }

  @Requirement("RIA-92")
  @Test
  void shouldAnnounceTheOpenedTicketAfterSavingIt() {
    givenFaultHandled(FaultHandling.OUT_OF_SERVICE_RESERVATION_CANCELLED);

    var opened = service.open("EB-901", "Flat tyre");

    var event = ArgumentCaptor.forClass(MaintenanceTicketOpened.class);
    var order = inOrder(fleet, tickets, events);
    order.verify(fleet).reportFault("EB-901");
    order.verify(tickets).save(any(MaintenanceTicket.class));
    order.verify(events).publishEvent(event.capture());
    assertThat(event.getValue())
        .isEqualTo(new MaintenanceTicketOpened(opened.ticket().getId(), VEHICLE, NOW));
  }

  @Requirement("RIA-92")
  @Test
  void shouldReportThatABikeInUseWaitsForItsTripToEnd() {
    givenFaultHandled(FaultHandling.PENDING_UNTIL_TRIP_ENDS);

    var opened = service.open("EB-901", "Strange noise reported by the guest");

    assertThat(opened.handling()).isEqualTo(FaultHandling.PENDING_UNTIL_TRIP_ENDS);
    assertThat(opened.ticket().getSlaDueAt()).isEqualTo(NOW.plus(SLA));
  }

  // The description is checked before the bike is touched, so an invalid request changes nothing.
  @Requirement("RIA-92")
  @Test
  void shouldRefuseInvalidDescriptionWithoutTouchingTheBike() {
    assertThatThrownBy(() -> service.open("EB-901", "  "))
        .isInstanceOf(InvalidTicketException.class);

    verifyNoInteractions(fleet, tickets, events);
  }

  @Requirement("RIA-92")
  @Test
  void shouldLookUpTheBikeByItsCodeWithoutSurroundingSpaces() {
    givenFaultHandled(FaultHandling.OUT_OF_SERVICE);

    var opened = service.open(" EB-901\t", "Flat tyre");

    assertThat(opened.vehicleCode()).isEqualTo("EB-901");
  }

  @Requirement("RIA-92")
  @Test
  void shouldNotOpenTicketForUnknownBike() {
    when(fleet.reportFault("EB-999")).thenThrow(new VehicleNotFoundException());

    assertThatThrownBy(() -> service.open("EB-999", "Flat tyre"))
        .isInstanceOf(VehicleNotFoundException.class);

    verify(tickets, never()).save(any());
    verifyNoInteractions(events);
  }

  @Requirement("RIA-92")
  @Test
  void shouldNotOpenTicketForRetiredBike() {
    when(fleet.reportFault("EB-901")).thenThrow(new VehicleRetiredException());

    assertThatThrownBy(() -> service.open("EB-901", "Flat tyre"))
        .isInstanceOf(VehicleRetiredException.class);

    verify(tickets, never()).save(any());
    verifyNoInteractions(events);
  }
}
