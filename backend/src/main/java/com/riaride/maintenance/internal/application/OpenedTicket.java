package com.riaride.maintenance.internal.application;

import com.riaride.fleet.FaultHandling;
import com.riaride.maintenance.internal.domain.MaintenanceTicket;

// A ticket just opened, with the bike's code and what the fleet did with the bike.
public record OpenedTicket(MaintenanceTicket ticket, String vehicleCode, FaultHandling handling) {}
