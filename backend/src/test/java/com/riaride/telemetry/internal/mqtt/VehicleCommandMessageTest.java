package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.MqttContracts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Unit test (Surefire): the UNLOCK command follows the simulator's command contract
// (contracts/mqtt/command.schema.json, RIA-60): topic riaride/vehicles/{code}/commands and the
// fields messageId, emittedAt, command and hubId, with no others (RIA-54).
class VehicleCommandMessageTest {

	private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
	private static final String HUB = "ded64655-0ca3-5c5c-9dd8-c641407a63c5";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Requirement("RIA-54")
	@Test
	void shouldAddressTheUnlockToTheVehicleCommandTopic() {
		var message = VehicleCommandMessage.unlock("EB-201", NOW);

		assertThat(message.topic()).isEqualTo("riaride/vehicles/EB-201/commands");
	}

	@Requirement("RIA-54")
	@Test
	void shouldCarryOnlyTheFieldsOfTheCommandContract() {
		var payload = JSON.readTree(VehicleCommandMessage.unlock("EB-201", NOW).payload(JSON));

		assertThat(payload.propertyNames()).containsExactlyInAnyOrder("messageId", "emittedAt", "command", "hubId");
		assertThat(payload.get("command").asString()).isEqualTo("UNLOCK");
		assertThat(payload.get("emittedAt").asString()).isEqualTo("2026-11-30T10:00:00Z");
		assertThat(payload.get("hubId").isNull()).isTrue();
		assertThat(UUID.fromString(payload.get("messageId").asString())).isNotNull();
	}

	// The simulator ignores a repeated messageId, so every command needs its own.
	@Requirement("RIA-54")
	@Test
	void shouldGiveEachCommandItsOwnMessageId() {
		var first = VehicleCommandMessage.unlock("EB-201", NOW);
		var second = VehicleCommandMessage.unlock("EB-201", NOW);

		assertThat(first.messageId()).isNotEqualTo(second.messageId());
	}

	@Requirement("RIA-54")
	@Test
	void shouldSerializeUnlockMatchingCommandSchema() {
		var message = VehicleCommandMessage.unlock("EB-201", NOW);
		var payload = new String(message.payload(JSON), StandardCharsets.UTF_8);

		assertThat(MqttContracts.commandViolations(payload)).isEmpty();
		assertThat(message.topic()).isEqualTo("riaride/vehicles/EB-201/commands");
		assertThat(JSON.readTree(payload).get("hubId").isNull()).isTrue();
	}

	// A failed start locks the bike again at the hub it was reserved at (RIA-54).
	@Requirement("RIA-54")
	@Test
	void shouldSerializeLockWithHubMatchingCommandSchema() {
		var message = VehicleCommandMessage.lock("EB-201", HUB, NOW);
		var payload = new String(message.payload(JSON), StandardCharsets.UTF_8);

		assertThat(MqttContracts.commandViolations(payload)).isEmpty();
		assertThat(message.topic()).isEqualTo("riaride/vehicles/EB-201/commands");
		var body = JSON.readTree(payload);
		assertThat(body.get("command").asString()).isEqualTo("LOCK");
		assertThat(body.get("hubId").asString()).isEqualTo(HUB);
	}

	// A bike with no known hub locks where it is.
	@Requirement("RIA-54")
	@Test
	void shouldSerializeLockWithoutHubMatchingCommandSchema() {
		var payload = new String(VehicleCommandMessage.lock("EB-201", null, NOW).payload(JSON), StandardCharsets.UTF_8);

		assertThat(MqttContracts.commandViolations(payload)).isEmpty();
		assertThat(JSON.readTree(payload).get("hubId").isNull()).isTrue();
	}

	// The contract test must be able to fail: a payload with an extra field breaks the schema.
	@Requirement("RIA-54")
	@Test
	void shouldRejectCommandWithFieldOutsideTheSchema() {
		var payload = """
			{"messageId": "3f6c1d2e-7a4b-4c1d-9e2f-0a1b2c3d4e5f", "emittedAt": "2026-11-30T10:00:00Z",
			 "command": "UNLOCK", "hubId": null, "tripId": "x"}
			""";

		assertThat(MqttContracts.commandViolations(payload)).isNotEmpty();
	}

}
