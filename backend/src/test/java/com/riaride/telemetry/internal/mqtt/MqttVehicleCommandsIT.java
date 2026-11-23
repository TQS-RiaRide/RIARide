package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestMosquitto;
import com.riaride.TestcontainersConfiguration;
import com.riaride.telemetry.VehicleCommands;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

// Integration test (Failsafe): the platform publishes UNLOCK on the vehicle's command topic of a
// real Mosquitto broker (ADR 0006), where the simulator listens (RIA-54).
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MqttVehicleCommandsIT {

	@DynamicPropertySource
	static void mosquitto(DynamicPropertyRegistry registry) {
		TestMosquitto.registerProperties(registry);
	}

	@Autowired
	private VehicleCommands vehicleCommands;

	@Autowired
	private JsonMapper json;

	@Requirement("RIA-54")
	@Test
	void shouldPublishAnUnlockOnTheVehicleCommandTopic() {
		try (var recorder = TestMosquitto.recordCommands()) {
			vehicleCommands.unlock("EB-201");

			await().atMost(Duration.ofSeconds(5)).until(() -> !recorder.commands().isEmpty());
			var command = recorder.commands().getFirst();
			assertThat(command.vehicleCode()).isEqualTo("EB-201");
			var payload = json.readTree(command.payload());
			assertThat(payload.get("command").asString()).isEqualTo("UNLOCK");
			assertThat(payload.get("hubId").isNull()).isTrue();
		}
	}

}
