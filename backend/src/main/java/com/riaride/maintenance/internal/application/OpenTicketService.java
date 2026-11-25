package com.riaride.maintenance.internal.application;

import com.riaride.fleet.VehicleMaintenance;
import com.riaride.maintenance.MaintenanceTicketOpened;
import com.riaride.maintenance.internal.domain.MaintenanceTicket;
import com.riaride.maintenance.internal.persistence.MaintenanceTicketRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// An operator opens a ticket for a faulty bike (RIA-92). The bike leaves service (or is flagged
// until its trip ends) and the ticket is saved in one transaction, so neither exists without the
// other. The ticket is due the SLA (24 hours) after it is opened.
@Service
public class OpenTicketService {

  private final VehicleMaintenance fleet;
  private final MaintenanceTicketRepository tickets;
  private final ApplicationEventPublisher events;
  private final MaintenanceProperties properties;
  private final Clock clock;

  public OpenTicketService(
      VehicleMaintenance fleet,
      MaintenanceTicketRepository tickets,
      ApplicationEventPublisher events,
      MaintenanceProperties properties,
      Clock clock) {
    this.fleet = fleet;
    this.tickets = tickets;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public OpenedTicket open(String vehicleCode, String description) {
    MaintenanceTicket.validDescription(description);
    var vehicle = fleet.reportFault(vehicleCode);
    var ticket =
        tickets.save(
            MaintenanceTicket.openByOperator(
                vehicle.vehicleId(), description, clock.instant(), properties.sla()));
    events.publishEvent(
        new MaintenanceTicketOpened(ticket.getId(), vehicle.vehicleId(), ticket.getOpenedAt()));
    return new OpenedTicket(ticket, vehicle.code(), vehicle.handling());
  }
}
