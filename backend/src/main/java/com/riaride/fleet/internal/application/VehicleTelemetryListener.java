package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import com.riaride.telemetry.VehicleTelemetryReceived;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Keeps each vehicle's latest battery from telemetry (RIA-59), so BR12 decides bookability on the
// current reading. It never changes the lifecycle status, and Vehicle.recordBattery ignores older
// and far-future readings. Deduplication by messageId and the hub, position and lock state are
// RIA-58.
//
// A synchronous @EventListener, not an @ApplicationModuleListener after commit: the reading must be
// stored before the MQTT message is acknowledged (architecture README, section 4, exception to the
// communication rule).
@Component
class VehicleTelemetryListener {

  private static final Logger log = LoggerFactory.getLogger(VehicleTelemetryListener.class);

  private final VehicleRepository vehicles;
  private final Clock clock;

  VehicleTelemetryListener(VehicleRepository vehicles, Clock clock) {
    this.vehicles = vehicles;
    this.clock = clock;
  }

  // In the publisher's transaction: the reading is stored before the MQTT message is acknowledged.
  // The row is locked: the update writes the whole vehicle, so without the lock a reading would
  // put back the status it read and undo a reservation made in the meantime (RIA-54). Lock order
  // on this path is vehicle, then trip; starting a trip locks only the vehicle. An unknown vehicle
  // is logged at DEBUG: the simulator's demo fleet publishes every 5 s, also for bikes that no
  // operator has registered (RIA-57).
  @EventListener
  @Transactional
  void on(VehicleTelemetryReceived reading) {
    var now = clock.instant();
    vehicles
        .findByCodeForUpdate(reading.vehicleCode())
        .ifPresentOrElse(
            vehicle ->
                vehicle.recordBattery(
                    BatteryLevel.of(reading.batteryPct()), reading.emittedAt(), now),
            () -> log.debug("Telemetry for unknown vehicle {} ignored", reading.vehicleCode()));
  }
}
