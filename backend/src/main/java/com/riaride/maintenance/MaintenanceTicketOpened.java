package com.riaride.maintenance;

import java.time.Instant;
import java.util.UUID;

// A ticket was opened for a faulty bike (RIA-92). Published after the ticket and the bike's new
// state are saved; the trips module cancels the bike's active reservation, if it has one.
public record MaintenanceTicketOpened(UUID ticketId, UUID vehicleId, Instant openedAt) {}
