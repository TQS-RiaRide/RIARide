package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import com.riaride.telemetry.VehicleTelemetryReceived;
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

	VehicleTelemetryListener(VehicleRepository vehicles) {
		this.vehicles = vehicles;
	}

	// Synchronous, in the publisher's transaction: the reading is stored before the MQTT message is
	// acknowledged.
	@EventListener
	@Transactional
	void on(VehicleTelemetryReceived reading) {
		vehicles.findByCode(reading.vehicleCode()).ifPresentOrElse(
			vehicle -> vehicle.recordBattery(BatteryLevel.of(reading.batteryPct()), reading.emittedAt()),
			() -> log.warn("Telemetry for unknown vehicle {} ignored", reading.vehicleCode()));
	}

}
