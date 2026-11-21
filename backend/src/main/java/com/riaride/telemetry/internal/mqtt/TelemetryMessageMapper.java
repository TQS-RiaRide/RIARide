package com.riaride.telemetry.internal.mqtt;

import com.riaride.telemetry.VehicleTelemetryReceived;
import java.time.Instant;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Turns a simulator telemetry message into a VehicleTelemetryReceived event. The vehicle code is
// only in the topic (riaride/vehicles/{code}/telemetry); the payload follows
// contracts/mqtt/telemetry.schema.json. This is the only class that knows the simulator's format.
class TelemetryMessageMapper {

	private static final Pattern TOPIC = Pattern.compile("riaride/vehicles/([^/]+)/telemetry");

	private final JsonMapper json;

	TelemetryMessageMapper(JsonMapper json) {
		this.json = json;
	}

	VehicleTelemetryReceived map(String topic, byte[] payload) {
		var match = TOPIC.matcher(topic);
		match.matches();
		var reading = json.readTree(payload);
		return new VehicleTelemetryReceived(
			match.group(1),
			reading.get("messageId").asString(),
			Instant.parse(reading.get("emittedAt").asString()),
			reading.get("batteryPct").asInt(),
			textOrNull(reading.get("hubId")),
			reading.get("lockState").asString());
	}

	private static String textOrNull(JsonNode node) {
		return node == null || node.isNull() ? null : node.asString();
	}

}
