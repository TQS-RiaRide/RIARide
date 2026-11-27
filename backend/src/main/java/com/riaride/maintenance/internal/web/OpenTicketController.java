package com.riaride.maintenance.internal.web;

import com.riaride.maintenance.internal.application.OpenTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// An operator opens a ticket for a faulty bike (RIA-92): POST /api/v1/tickets.
// SecurityConfiguration
// also restricts it to these roles, so another role gets 403 before the body is read. Reading a
// ticket back comes with the ticket list (RIA-93, RIA-120), so the response has no Location yet.
@RestController
class OpenTicketController {

  private final OpenTicketService openTicket;

  OpenTicketController(OpenTicketService openTicket) {
    this.openTicket = openTicket;
  }

  @PostMapping("/api/v1/tickets")
  @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  TicketResponse open(@RequestBody OpenTicketRequest request) {
    return TicketResponse.from(openTicket.open(request.vehicleCode(), request.description()));
  }
}
