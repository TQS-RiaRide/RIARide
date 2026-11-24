package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestMosquitto;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// Integration test (Failsafe, Mosquitto in Testcontainers): the kept session survives a restart.
// While the backend is down the broker queues the telemetry for its client id, and on reconnection
// it sends that backlog right after CONNACK, before the SUBACK. The subscriber must handle the
// whole
// backlog and keep handling new messages, however long the backlog is.
class MqttTelemetrySubscriberIT {

  // More than Paho's inbound queue of 10 messages, and the simulator's 12 bikes.
  private static final int BACKLOG = 15;
  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  // The probe has its own vehicle code and its own counter: a probe published just before the
  // subscriber stops is queued with the backlog and must not count as one of its messages.
  private static final String PROBE = "EB-PROBE";

  private final AtomicInteger probes = new AtomicInteger();
  private final AtomicInteger handled = new AtomicInteger();
  private final TelemetryMessageHandler handler = countingHandler();
  private final TelemetryMqttProperties properties =
      new TelemetryMqttProperties(
          true, TestMosquitto.url(), "riaride-backend-it-" + UUID.randomUUID(), null, null);
  private MqttTelemetrySubscriber subscriber;

  @AfterEach
  void stopSubscriber() {
    if (subscriber != null) {
      subscriber.stop();
    }
  }

  @Requirement("RIA-59")
  @Test
  void shouldHandleTheQueuedBacklogAndNewMessagesAfterARestart() {
    subscriber = startAndAwaitSubscription();
    subscriber.stop();
    for (int i = 0; i < BACKLOG; i++) {
      TestMosquitto.publishTelemetry("EB-" + (700 + i), 50, null);
    }

    subscriber = new MqttTelemetrySubscriber(properties, handler);
    subscriber.start();
    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(handled.get()).isEqualTo(BACKLOG));
    TestMosquitto.publishTelemetry("EB-799", 50, null);

    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(handled.get()).isEqualTo(BACKLOG + 1));
  }

  // The broker keeps a session only after the first subscription, so the test waits until a probe
  // message gets through before stopping the subscriber. Probes do not count as handled backlog.
  private MqttTelemetrySubscriber startAndAwaitSubscription() {
    var started = new MqttTelemetrySubscriber(properties, handler);
    started.start();
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          TestMosquitto.publishTelemetry(PROBE, 50, null);
          return probes.get() > 0;
        });
    return started;
  }

  private TelemetryMessageHandler countingHandler() {
    var counting = mock(TelemetryMessageHandler.class);
    doAnswer(
            invocation -> {
              String topic = invocation.getArgument(0);
              return topic.contains("/" + PROBE + "/")
                  ? probes.incrementAndGet()
                  : handled.incrementAndGet();
            })
        .when(counting)
        .handle(anyString(), any());
    return counting;
  }
}
