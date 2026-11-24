package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): an exception while handling a message never reaches Paho. Paho would drop
// the connection, and the broker would redeliver the same message forever (QoS 1, kept session).
class MqttTelemetrySubscriberTest {

  @Requirement("RIA-59")
  @Test
  void shouldDropAMessageWhoseHandlingFails() {
    var handler = mock(TelemetryMessageHandler.class);
    doThrow(new IllegalStateException("database down")).when(handler).handle(anyString(), any());
    var subscriber =
        new MqttTelemetrySubscriber(
            new TelemetryMqttProperties(
                true, "tcp://localhost:1883", "riaride-backend", null, null),
            handler);

    assertThatCode(() ->
            subscriber.messageArrived(
                "riaride/vehicles/EB-601/telemetry",
                new MqttMessage("{}".getBytes(StandardCharsets.UTF_8))))
        .doesNotThrowAnyException();
  }

  @Requirement("RIA-59")
  @Test
  void shouldStopCleanlyWhenItNeverStarted() {
    var subscriber =
        new MqttTelemetrySubscriber(
            new TelemetryMqttProperties(
                true, "tcp://localhost:1883", "riaride-backend", null, null),
            mock(TelemetryMessageHandler.class));

    assertThatCode(subscriber::stop).doesNotThrowAnyException();
  }
}
