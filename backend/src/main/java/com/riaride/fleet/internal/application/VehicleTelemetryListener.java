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
// current reading. It never changes the lifecycle status. Deduplication by messageId, stale
// readings and the hub, position and lock state are RIA-58.
@Component
class VehicleTelemetryListener {

	private static final Logger log = LoggerFactory.getLogger(VehicleTelemetryListener.class);

	private final VehicleRepository vehicles;
	private final Clock clock;

	VehicleTelemetryListener(VehicleRepository vehicles, Clock clock) {
		this.vehicles = vehicles;
		this.clock = clock;
	}

	// Synchronous, in the publisher's transaction: the reading is stored before the MQTT message is
	// acknowledged. The row is locked: the update writes the whole vehicle, so without the lock a
	// reading would put back the status it read and undo a reservation made in the meantime (RIA-54).
	// Lock order on this path is vehicle, then trip; starting a trip locks only the vehicle.
	@EventListener
	@Transactional
	void on(VehicleTelemetryReceived reading) {
		var now = clock.instant();
		vehicles.findByCodeForUpdate(reading.vehicleCode()).ifPresentOrElse(
			vehicle -> vehicle.recordBattery(BatteryLevel.of(reading.batteryPct()), reading.emittedAt(), now),
			() -> log.warn("Telemetry for unknown vehicle {} ignored", reading.vehicleCode()));
	}

}
