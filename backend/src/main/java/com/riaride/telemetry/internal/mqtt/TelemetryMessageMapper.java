package com.riaride.telemetry.internal.mqtt;

import com.riaride.telemetry.VehicleTelemetryReceived;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Turns a simulator telemetry message into a VehicleTelemetryReceived event. The vehicle code is
// only in the topic (riaride/vehicles/{code}/telemetry); the payload follows
// contracts/mqtt/telemetry.schema.json. This is the only class that knows the simulator's format.
// The full JSON Schema contract test belongs to RIA-58; here only the fields the event needs are
// checked.
class TelemetryMessageMapper {

	private static final Pattern TOPIC = Pattern.compile("riaride/vehicles/([A-Za-z0-9-]{1,32})/telemetry");
	private static final int MIN_BATTERY = 0;
	private static final int MAX_BATTERY = 100;

	private final JsonMapper json;

	TelemetryMessageMapper(JsonMapper json) {
		this.json = json;
	}

	VehicleTelemetryReceived map(String topic, byte[] payload) {
		var match = TOPIC.matcher(topic == null ? "" : topic);
		if (!match.matches()) {
			throw new InvalidTelemetryMessageException("Not a vehicle telemetry topic.");
		}
		var reading = readTree(payload);
		return new VehicleTelemetryReceived(
			match.group(1),
			requiredText(reading, "messageId"),
			instant(requiredText(reading, "emittedAt")),
			battery(reading),
			optionalText(reading, "hubId"),
			requiredText(reading, "lockState"));
	}

	private JsonNode readTree(byte[] payload) {
		try {
			return json.readTree(payload);
		} catch (JacksonException notJson) {
			throw new InvalidTelemetryMessageException("The payload is not JSON.", notJson);
		}
	}

	private static String requiredText(JsonNode reading, String field) {
		var node = reading.get(field);
		if (node == null || !node.isString()) {
			throw new InvalidTelemetryMessageException("Missing or invalid field " + field + ".");
		}
		return node.asString();
	}

	private static String optionalText(JsonNode reading, String field) {
		var node = reading.get(field);
		return node == null || node.isNull() ? null : node.asString();
	}

	private static Instant instant(String value) {
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException notATimestamp) {
			throw new InvalidTelemetryMessageException("Invalid field emittedAt.", notATimestamp);
		}
	}

	private static int battery(JsonNode reading) {
		var node = reading.get("batteryPct");
		if (node == null || !node.isInt() || node.asInt() < MIN_BATTERY || node.asInt() > MAX_BATTERY) {
			throw new InvalidTelemetryMessageException("Missing or invalid field batteryPct.");
		}
		return node.asInt();
	}

}
