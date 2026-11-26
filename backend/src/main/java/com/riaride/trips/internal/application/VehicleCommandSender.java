package com.riaride.trips.internal.application;

import com.riaride.telemetry.VehicleCommands;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Sends the trips module's commands to the bike only after the transaction that decided them
// commits. The simulator answers UNLOCK with telemetry at once, so the trip must already be stored
// when that answer arrives; and a rolled-back start must never unlock a bike. An ended trip
// (RIA-55) locks the bike at the hub it was returned to.
@Component
class VehicleCommandSender {

  private static final Logger log = LoggerFactory.getLogger(VehicleCommandSender.class);

  private final VehicleCommands commands;

  VehicleCommandSender(VehicleCommands commands) {
    this.commands = commands;
  }

  @TransactionalEventListener
  void on(TripStartRequested requested) {
    send("UNLOCK", requested.vehicleCode(), () -> commands.unlock(requested.vehicleCode()));
  }

  @TransactionalEventListener
  void on(TripFailed failed) {
    send(
        "LOCK",
        failed.vehicleCode(),
        () -> commands.lock(failed.vehicleCode(), Objects.toString(failed.hubId(), null)));
  }

  @TransactionalEventListener
  void on(TripEnded ended) {
    send(
        "LOCK",
        ended.vehicleCode(),
        () -> commands.lock(ended.vehicleCode(), ended.hubId().toString()));
  }

  // The trip is already stored: a command that fails here must not turn the guest's 202 into a 500,
  // nor stop the telemetry thread. A lost UNLOCK makes the trip time out (BR13).
  private static void send(String command, String vehicleCode, Runnable sending) {
    try {
      sending.run();
    } catch (RuntimeException failed) {
      log.error(
          "{} for vehicle {} not sent ({})",
          command,
          vehicleCode,
          failed.getClass().getSimpleName());
    }
  }
}
