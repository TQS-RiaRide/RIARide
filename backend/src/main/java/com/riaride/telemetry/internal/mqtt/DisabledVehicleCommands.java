package com.riaride.telemetry.internal.mqtt;

import com.riaride.telemetry.VehicleCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Used when riaride.telemetry.mqtt.enabled=false (most tests, or a run without a broker): commands
// are logged and dropped, so the modules that send them still start.
class DisabledVehicleCommands implements VehicleCommands {

	private static final Logger log = LoggerFactory.getLogger(DisabledVehicleCommands.class);

	@Override
	public void unlock(String vehicleCode) {
		log.warn("MQTT is turned off; UNLOCK for vehicle {} dropped", vehicleCode);
	}

}
