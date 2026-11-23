package com.riaride.trips.internal.application;

import com.riaride.telemetry.VehicleCommands;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Sends the trips module's commands to the bike only after the transaction that decided them
// commits. The simulator answers UNLOCK with telemetry at once, so the trip must already be stored
// when that answer arrives; and a rolled-back start must never unlock a bike.
@Component
class VehicleCommandSender {

	private final VehicleCommands commands;

	VehicleCommandSender(VehicleCommands commands) {
		this.commands = commands;
	}

	@TransactionalEventListener
	void on(TripStartRequested requested) {
		commands.unlock(requested.vehicleCode());
	}

	@TransactionalEventListener
	void on(TripFailed failed) {
		commands.lock(failed.vehicleCode(), Objects.toString(failed.hubId(), null));
	}

}
