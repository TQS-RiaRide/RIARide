package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Unit test (Surefire): the UNLOCK command follows the simulator's command contract
// (contracts/mqtt/command.schema.json, RIA-60): topic riaride/vehicles/{code}/commands and the
// fields messageId, emittedAt, command and hubId, with no others (RIA-54).
class VehicleCommandMessageTest {

	private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
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

}
