package com.riaride;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

// One Mosquitto broker per JVM (the same image as the simulator, ADR 0006), shared by the tests
// that send telemetry over MQTT. Anonymous access is fine here: the broker only lives for the
// build.
public final class TestMosquitto {

  private static final int MQTT_PORT = 1883;

  // Just after the tests' fixed clock: the platform ignores readings more than a minute ahead of
  // its clock, so the timestamp follows FixedClockConfiguration.NOW instead of a literal.
  private static final Instant EMITTED_AT = FixedClockConfiguration.NOW.plusSeconds(5);

  @SuppressWarnings("resource")
  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>("eclipse-mosquitto:2.0")
          .withExposedPorts(MQTT_PORT)
          .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
          .waitingFor(Wait.forListeningPort());

  private TestMosquitto() {}

  public static synchronized String url() {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    return "tcp://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(MQTT_PORT);
  }

  // Freezes the broker (docker pause): connections stay open but nothing is answered, as with an
  // overloaded broker or a half-open connection. Always resume it, since the broker is shared.
  public static void pause() {
    url();
    CONTAINER.getDockerClient().pauseContainerCmd(CONTAINER.getContainerId()).exec();
  }

  public static void resume() {
    CONTAINER.getDockerClient().unpauseContainerCmd(CONTAINER.getContainerId()).exec();
  }

  // Turns on the platform's MQTT adapter against this broker.
  public static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("riaride.telemetry.mqtt.enabled", () -> "true");
    registry.add("riaride.telemetry.mqtt.url", TestMosquitto::url);
    registry.add(
        "riaride.telemetry.mqtt.client-id", () -> "riaride-backend-test-" + UUID.randomUUID());
  }

  // Publishes a telemetry reading as the simulator does (contracts/mqtt/telemetry.schema.json).
  public static void publishTelemetry(String vehicleCode, int batteryPct, String hubId) {
    publishTelemetry(vehicleCode, batteryPct, hubId, "LOCKED", EMITTED_AT);
  }

  // The same, with the lock state and emission time the scenario needs (RIA-54: the simulator
  // answers UNLOCK with an UNLOCKED reading).
  public static void publishTelemetry(
      String vehicleCode, int batteryPct, String hubId, String lockState, Instant emittedAt) {
    var payload =
        """
        {"messageId": "%s", "emittedAt": "%s", "batteryPct": %d, "lockState": "%s",
         "hubId": %s, "position": {"lat": 40.6405, "lon": -8.6538}}
        """
            .formatted(
                UUID.randomUUID(),
                emittedAt,
                batteryPct,
                lockState,
                hubId == null ? "null" : "\"" + hubId + "\"");
    try (var client =
        new MqttClient(
            url(), "riaride-simulator-test-" + UUID.randomUUID(), new MemoryPersistence())) {
      client.connect();
      client.publish(
          "riaride/vehicles/" + vehicleCode + "/telemetry",
          payload.getBytes(StandardCharsets.UTF_8),
          1,
          false);
      client.disconnect();
    } catch (MqttException failed) {
      throw new IllegalStateException("Could not publish telemetry to the test broker", failed);
    }
  }

  // Listens as the simulator does on every vehicle's command topic (RIA-54). Open it before the
  // action that sends the command: the broker does not keep non-retained messages for late
  // subscribers.
  public static CommandRecorder recordCommands() {
    return new CommandRecorder(url());
  }

  // command is UNLOCK or LOCK, read from the payload; payload is the JSON as published.
  public record RecordedCommand(String vehicleCode, String command, String payload) {}

  public static final class CommandRecorder implements AutoCloseable {

    private static final Pattern COMMAND_TOPIC =
        Pattern.compile("riaride/vehicles/([^/]+)/commands");
    private static final Pattern COMMAND_FIELD =
        Pattern.compile("\"command\"\\s*:\\s*\"([A-Z]+)\"");

    private final List<RecordedCommand> commands = new CopyOnWriteArrayList<>();
    private final MqttClient client;

    private CommandRecorder(String url) {
      try {
        client =
            new MqttClient(
                url, "riaride-simulator-test-" + UUID.randomUUID(), new MemoryPersistence());
        client.connect();
        client.subscribe(
            "riaride/vehicles/+/commands",
            1,
            (topic, message) -> {
              var match = COMMAND_TOPIC.matcher(topic);
              if (match.matches()) {
                var payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                var command = COMMAND_FIELD.matcher(payload);
                commands.add(
                    new RecordedCommand(
                        match.group(1), command.find() ? command.group(1) : null, payload));
              }
            });
      } catch (MqttException failed) {
        throw new IllegalStateException("Could not subscribe to the test broker", failed);
      }
    }

    public List<RecordedCommand> commands() {
      return List.copyOf(commands);
    }

    @Override
    public void close() {
      try {
        client.disconnect();
        client.close();
      } catch (MqttException ignored) {
        // The broker only lives for the build.
      }
    }
  }
}
