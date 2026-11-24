package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.telemetry.VehicleTelemetryReceived;
import org.junit.jupiter.api.Test;
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

}
