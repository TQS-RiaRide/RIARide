package com.riaride;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

// One Mosquitto broker per JVM (the same image as the simulator, ADR 0006), shared by the tests
// that send telemetry over MQTT. Anonymous access is fine here: the broker only lives for the build.
public final class TestMosquitto {

	private static final int MQTT_PORT = 1883;

	// Just after the tests' fixed clock: the platform ignores readings more than a minute ahead of
	// its clock, so the timestamp follows FixedClockConfiguration.NOW instead of a literal.
	private static final Instant EMITTED_AT = FixedClockConfiguration.NOW.plusSeconds(5);

	@SuppressWarnings("resource")
	private static final GenericContainer<?> CONTAINER = new GenericContainer<>("eclipse-mosquitto:2.0")
		.withExposedPorts(MQTT_PORT)
		.withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
		.waitingFor(Wait.forListeningPort());

	private TestMosquitto() {
	}

	public static synchronized String url() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		return "tcp://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(MQTT_PORT);
	}

	// Turns on the platform's MQTT adapter against this broker.
	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("riaride.telemetry.mqtt.enabled", () -> "true");
		registry.add("riaride.telemetry.mqtt.url", TestMosquitto::url);
		registry.add("riaride.telemetry.mqtt.client-id", () -> "riaride-backend-test-" + UUID.randomUUID());
	}

	// Publishes a telemetry reading as the simulator does (contracts/mqtt/telemetry.schema.json).
	public static void publishTelemetry(String vehicleCode, int batteryPct, String hubId) {
		var payload = """
			{"messageId": "%s", "emittedAt": "%s", "batteryPct": %d, "lockState": "LOCKED",
			 "hubId": %s, "position": {"lat": 40.6405, "lon": -8.6538}}
			""".formatted(UUID.randomUUID(), EMITTED_AT, batteryPct, hubId == null ? "null" : "\"" + hubId + "\"");
		try (var client = new MqttClient(url(), "riaride-simulator-test-" + UUID.randomUUID(), new MemoryPersistence())) {
			client.connect();
			client.publish("riaride/vehicles/" + vehicleCode + "/telemetry", payload.getBytes(StandardCharsets.UTF_8), 1,
				false);
			client.disconnect();
		} catch (MqttException failed) {
			throw new IllegalStateException("Could not publish telemetry to the test broker", failed);
		}
	}

}
