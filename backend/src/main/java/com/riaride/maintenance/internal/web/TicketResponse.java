package com.riaride.maintenance.internal.web;

import com.riaride.fleet.FaultHandling;
import com.riaride.maintenance.internal.application.OpenedTicket;
import java.time.Instant;
import java.util.UUID;

// A ticket just opened, with the state the bike was left in: in maintenance, or still in use and
// flagged (maintenancePending) until its trip ends.
record TicketResponse(
    UUID id,
    String vehicleCode,
    String status,
    String description,
    Instant openedAt,
    Instant slaDueAt,
    String vehicleStatus,
    boolean maintenancePending) {

  static TicketResponse from(OpenedTicket opened) {
    var ticket = opened.ticket();
    var pending = opened.handling() == FaultHandling.PENDING_UNTIL_TRIP_ENDS;
    return new TicketResponse(
        ticket.getId(),
        opened.vehicleCode(),
        ticket.getStatus().name(),
        ticket.getDescription(),
        ticket.getOpenedAt(),
        ticket.getSlaDueAt(),
        pending ? "IN_USE" : "MAINTENANCE",
        pending);
  }
}
