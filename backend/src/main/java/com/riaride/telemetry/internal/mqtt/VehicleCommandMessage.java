package com.riaride.telemetry.internal.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

// A command in the simulator's format (contracts/mqtt/command.schema.json): published on
// riaride/vehicles/{code}/commands with messageId, emittedAt, command and hubId, and nothing else.
// hubId is only set by LOCK, which ends a ride at a hub (RIA-55).
record VehicleCommandMessage(String vehicleCode, String messageId, Instant emittedAt, String command, String hubId) {

	static VehicleCommandMessage unlock(String vehicleCode, Instant now) {
		return new VehicleCommandMessage(vehicleCode, UUID.randomUUID().toString(), now, "UNLOCK", null);
	}

	String topic() {
		return "riaride/vehicles/" + vehicleCode + "/commands";
	}

	byte[] payload(JsonMapper json) {
		var body = json.createObjectNode()
			.put("messageId", messageId)
			.put("emittedAt", emittedAt.toString())
			.put("command", command);
		if (hubId == null) {
			body.putNull("hubId");
		} else {
			body.put("hubId", hubId);
		}
		return json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
	}

}
