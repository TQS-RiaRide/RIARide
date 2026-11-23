package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.awaitility.Awaitility.await;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.MqttContracts;
import com.riaride.TestMosquitto;
import com.riaride.TestcontainersConfiguration;
import com.riaride.telemetry.VehicleCommands;
import com.riaride.telemetry.VehicleUnlocked;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

// Integration test (Failsafe): the MQTT adapter against a real Mosquitto broker (ADR 0006). The
// platform publishes UNLOCK and LOCK on the vehicle's command topic, where the simulator listens,
// and an UNLOCKED reading from the simulator becomes a VehicleUnlocked event (RIA-54).
@SpringBootTest
@Import({TestcontainersConfiguration.class, MqttVehicleCommandsIT.UnlockedEvents.class})
class MqttVehicleCommandsIT {

	private static final Duration MQTT_DELIVERY = Duration.ofSeconds(5);

	@DynamicPropertySource
	static void mosquitto(DynamicPropertyRegistry registry) {
		TestMosquitto.registerProperties(registry);
	}

	// Telemetry arrives on Paho's callback thread, and @RecordApplicationEvents only records events
	// of the test's own thread, so the events are collected by a listener instead.
	@TestConfiguration(proxyBeanMethods = false)
	static class UnlockedEvents {

		private final List<VehicleUnlocked> received = new CopyOnWriteArrayList<>();

		@EventListener
		void on(VehicleUnlocked event) {
			received.add(event);
		}

		List<VehicleUnlocked> of(String vehicleCode) {
			return received.stream().filter(event -> event.vehicleCode().equals(vehicleCode)).toList();
		}

	}

	@Autowired
	private VehicleCommands vehicleCommands;

	@Autowired
	private UnlockedEvents unlockedEvents;

	@Autowired
	private JsonMapper json;

	@Requirement("RIA-54")
	@Test
	void shouldPublishUnlockCommandToVehicleTopic() {
		try (var recorder = TestMosquitto.recordCommands()) {
			vehicleCommands.unlock("EB-201");

			await().atMost(MQTT_DELIVERY).until(() -> !recorder.commands().isEmpty());
			var command = recorder.commands().getFirst();
			assertThat(command.vehicleCode()).isEqualTo("EB-201");
			assertThat(command.command()).isEqualTo("UNLOCK");
			assertThat(MqttContracts.commandViolations(command.payload())).isEmpty();
			assertThat(json.readTree(command.payload()).get("hubId").isNull()).isTrue();
		}
	}

	@Requirement("RIA-54")
	@Test
	void shouldPublishLockCommandWithHubToVehicleTopic() {
		try (var recorder = TestMosquitto.recordCommands()) {
			vehicleCommands.lock("EB-202", "ded64655-0ca3-5c5c-9dd8-c641407a63c5");

			await().atMost(MQTT_DELIVERY).until(() -> !recorder.commands().isEmpty());
			var command = recorder.commands().getFirst();
			assertThat(command.vehicleCode()).isEqualTo("EB-202");
			assertThat(command.command()).isEqualTo("LOCK");
			assertThat(MqttContracts.commandViolations(command.payload())).isEmpty();
			assertThat(json.readTree(command.payload()).get("hubId").asString())
				.isEqualTo("ded64655-0ca3-5c5c-9dd8-c641407a63c5");
		}
	}

	@Requirement("RIA-54")
	@Test
	void shouldPublishVehicleUnlockedWhenSimulatorReportsUnlocked() {
		var emittedAt = Instant.parse("2026-11-30T10:00:07Z");

		TestMosquitto.publishTelemetry("EB-203", 80, null, "UNLOCKED", emittedAt);

		await().atMost(MQTT_DELIVERY).until(() -> !unlockedEvents.of("EB-203").isEmpty());
		assertThat(unlockedEvents.of("EB-203")).singleElement()
			.satisfies(event -> assertThat(event.emittedAt()).isEqualTo(emittedAt));
	}

	@Requirement("RIA-54")
	@Test
	void shouldNotPublishVehicleUnlockedWhenSimulatorReportsLocked() {
		TestMosquitto.publishTelemetry("EB-204", 80, null, "LOCKED", Instant.parse("2026-11-30T10:00:07Z"));
		TestMosquitto.publishTelemetry("EB-204", 80, null, "UNLOCKED", Instant.parse("2026-11-30T10:00:08Z"));

		// Messages of one vehicle arrive in order (QoS 1, one topic), so once the second one is
		// handled the first one was too.
		await().atMost(MQTT_DELIVERY).until(() -> !unlockedEvents.of("EB-204").isEmpty());
		assertThat(unlockedEvents.of("EB-204")).singleElement()
			.satisfies(event -> assertThat(event.emittedAt()).isEqualTo(Instant.parse("2026-11-30T10:00:08Z")));
	}

	// A command is sent in the request (after commit) or on the telemetry thread: a broker that
	// stops answering must not hold either for longer than a few seconds. The command is dropped and
	// the trip times out.
	@Requirement("RIA-54")
	@Test
	void shouldReturnWithinSecondsWhenTheBrokerStopsAnswering() {
		TestMosquitto.pause();
		try {
			assertTimeoutPreemptively(Duration.ofSeconds(5), () -> vehicleCommands.unlock("EB-205"));
		} finally {
			TestMosquitto.resume();
		}
	}

	// k6 starts trips with 50 virtual users: every UNLOCK sent at the same moment must reach the
	// broker, none dropped because too many wait for their acknowledgement.
	@Requirement("RIA-54")
	@Test
	void shouldPublishEveryUnlockWhenManyTripsStartAtOnce() throws Exception {
		var trips = 50;
		try (var recorder = TestMosquitto.recordCommands(); var threads = Executors.newFixedThreadPool(trips)) {
			var go = new CountDownLatch(1);
			var sent = IntStream.range(0, trips)
				.mapToObj(i -> threads.submit(() -> {
					go.await();
					vehicleCommands.unlock("EB-5" + String.format("%02d", i));
					return null;
				}))
				.toList();
			go.countDown();
			for (var send : sent) {
				send.get();
			}

			await().atMost(MQTT_DELIVERY).until(() -> recorder.commands().stream()
				.filter(command -> command.vehicleCode().startsWith("EB-5")).count() == trips);
		}
	}

}
