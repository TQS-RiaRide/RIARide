package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Unit test (Surefire): a telemetry message (topic riaride/vehicles/{code}/telemetry, payload as in
// contracts/mqtt/telemetry.schema.json) becomes a VehicleTelemetryReceived event.
class TelemetryMessageMapperTest {

	private final TelemetryMessageMapper mapper = new TelemetryMessageMapper(JsonMapper.builder().build());

	static byte[] payload(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

	static final String DOCKED_READING = """
		{"messageId": "3f6c1d2e-7a4b-4c1d-9e2f-0a1b2c3d4e5f", "emittedAt": "2026-11-30T10:00:05.123Z",
		 "batteryPct": 18, "lockState": "LOCKED", "hubId": "ded64655-0ca3-5c5c-9dd8-c641407a63c5",
		 "position": {"lat": 40.6405, "lon": -8.6538}}
		""";

	@Requirement("RIA-59")
	@Test
	void shouldTakeTheVehicleCodeFromTheTopicAndTheReadingFromThePayload() {
		var event = mapper.map("riaride/vehicles/EB-601/telemetry", payload(DOCKED_READING));

		assertThat(event.vehicleCode()).isEqualTo("EB-601");
		assertThat(event.messageId()).isEqualTo("3f6c1d2e-7a4b-4c1d-9e2f-0a1b2c3d4e5f");
		assertThat(event.emittedAt()).isEqualTo(Instant.parse("2026-11-30T10:00:05.123Z"));
		assertThat(event.batteryPct()).isEqualTo(18);
		assertThat(event.lockState()).isEqualTo("LOCKED");
		assertThat(event.hubId()).isEqualTo("ded64655-0ca3-5c5c-9dd8-c641407a63c5");
	}

	@Requirement("RIA-59")
	@Test
	void shouldKeepANullHubWhileTheVehicleIsRidden() {
		var riding = DOCKED_READING.replace("\"ded64655-0ca3-5c5c-9dd8-c641407a63c5\"", "null");

		var event = mapper.map("riaride/vehicles/EB-601/telemetry", payload(riding));

		assertThat(event.hubId()).isNull();
	}

}
