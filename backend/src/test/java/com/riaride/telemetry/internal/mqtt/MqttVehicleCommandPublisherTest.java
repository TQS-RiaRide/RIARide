package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThatCode;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Unit test (Surefire): sending a command never fails the caller. A trip whose UNLOCK is lost
// simply times out and fails (RIA-54), so a broker that is down must not break starting a trip.
class MqttVehicleCommandPublisherTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-30T10:00:00Z"), ZoneOffset.UTC);
	private static final TelemetryMqttProperties PROPERTIES =
		new TelemetryMqttProperties(true, "tcp://localhost:1", "riaride-backend", null, null);

	@Requirement("RIA-54")
	@Test
	void shouldDropAnUnlockWhenNotConnected() {
		var publisher = new MqttVehicleCommandPublisher(PROPERTIES, JsonMapper.builder().build(), CLOCK);

		assertThatCode(() -> publisher.unlock("EB-201")).doesNotThrowAnyException();
	}

	@Requirement("RIA-54")
	@Test
	void shouldStopCleanlyWhenItNeverStarted() {
		var publisher = new MqttVehicleCommandPublisher(PROPERTIES, JsonMapper.builder().build(), CLOCK);

		assertThatCode(publisher::stop).doesNotThrowAnyException();
	}

	@Requirement("RIA-54")
	@Test
	void shouldDropAnUnlockWhenMqttIsTurnedOff() {
		assertThatCode(() -> new DisabledVehicleCommands().unlock("EB-201")).doesNotThrowAnyException();
	}

}
