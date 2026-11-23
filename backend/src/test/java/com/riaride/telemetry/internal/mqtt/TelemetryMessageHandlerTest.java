package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.telemetry.VehicleTelemetryReceived;
import com.riaride.telemetry.VehicleUnlocked;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

// Unit test (Surefire): each MQTT telemetry message is published as an event; a message that is
// not a valid reading is dropped, so it never stops the subscription.
class TelemetryMessageHandlerTest {

	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final TelemetryMessageHandler handler = new TelemetryMessageHandler(
		new TelemetryMessageMapper(JsonMapper.builder().build()), events);

	@Requirement("RIA-59")
	@Test
	void shouldPublishAValidReadingAsAVehicleTelemetryEvent() {
		handler.handle("riaride/vehicles/EB-601/telemetry",
			TelemetryMessageMapperTest.payload(TelemetryMessageMapperTest.DOCKED_READING));

		verify(events).publishEvent(any(VehicleTelemetryReceived.class));
	}

	@Requirement("RIA-59")
	@Test
	void shouldDropAMessageThatIsNotAValidReading() {
		assertThatCode(() -> handler.handle("riaride/vehicles/EB-601/telemetry",
			TelemetryMessageMapperTest.payload("not json")))
			.doesNotThrowAnyException();

		verify(events, never()).publishEvent(any(Object.class));
	}

	// RIA-54: the simulator answers UNLOCK with an UNLOCKED reading, which confirms a starting trip.
	@Requirement("RIA-54")
	@Test
	void shouldPublishVehicleUnlockedWhenReadingIsUnlocked() {
		var unlocked = TelemetryMessageMapperTest.DOCKED_READING
			.replace("\"LOCKED\"", "\"UNLOCKED\"")
			.replace("\"ded64655-0ca3-5c5c-9dd8-c641407a63c5\"", "null");

		handler.handle("riaride/vehicles/EB-601/telemetry", TelemetryMessageMapperTest.payload(unlocked));

		var published = ArgumentCaptor.forClass(Object.class);
		verify(events, times(2)).publishEvent(published.capture());
		assertThat(published.getAllValues().getFirst()).isInstanceOf(VehicleTelemetryReceived.class);
		assertThat(published.getAllValues().getLast()).isEqualTo(new VehicleUnlocked("EB-601",
			"3f6c1d2e-7a4b-4c1d-9e2f-0a1b2c3d4e5f", Instant.parse("2026-11-30T10:00:05.123Z")));
	}

	@Requirement("RIA-54")
	@Test
	void shouldNotPublishVehicleUnlockedWhenReadingIsLocked() {
		handler.handle("riaride/vehicles/EB-601/telemetry",
			TelemetryMessageMapperTest.payload(TelemetryMessageMapperTest.DOCKED_READING));

		verify(events, never()).publishEvent(any(VehicleUnlocked.class));
	}

}
