package com.riaride.telemetry.internal.mqtt;

import com.riaride.telemetry.VehicleCommands;
import java.time.Clock;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.json.JsonMapper;

// The MQTT adapter's command side (ADR 0006): publishes each command with QoS 1 on the vehicle's
// command topic. It uses its own client id (<client-id>-commands), so it does not take over the
// subscriber's kept session. A broker that is down at startup does not stop the application: the
// first connection is retried in the background, and Paho reconnects after a lost connection. A
// command sent while disconnected is logged and dropped, so the trip times out (RIA-54).
class MqttVehicleCommandPublisher implements VehicleCommands, SmartLifecycle {

  private static final int QOS = 1;
  // How long a command waits for the broker's acknowledgement before it is logged as not sent.
  private static final long ACK_TIMEOUT_MS = 2_000;
  // Commands awaiting acknowledgement at once. Paho's default of 10 dropped UNLOCKs when many trips
  // start together; this is well above the load target of 50 concurrent starts.
  private static final int MAX_IN_FLIGHT = 1_000;

  private static final Logger log = LoggerFactory.getLogger(MqttVehicleCommandPublisher.class);

  private final JsonMapper json;
  private final Clock clock;
  private final MqttConnection connection;

  MqttVehicleCommandPublisher(TelemetryMqttProperties properties, JsonMapper json, Clock clock) {
    this.json = json;
    this.clock = clock;
    var options = new MqttConnectOptions();
    options.setMaxInflight(MAX_IN_FLIGHT);
    this.connection =
        new MqttConnection(
            properties, properties.clientId() + "-commands", "mqtt-commands-connect", options);
  }

  @Override
  public void unlock(String vehicleCode) {
    publish(VehicleCommandMessage.unlock(vehicleCode, clock.instant()));
  }

  @Override
  public void lock(String vehicleCode, String hubId) {
    publish(VehicleCommandMessage.lock(vehicleCode, hubId, clock.instant()));
  }

  private void publish(VehicleCommandMessage message) {
    var current = connection.client();
    if (current == null || !current.isConnected()) {
      log.warn(
          "MQTT broker not connected; {} for vehicle {} dropped",
          message.command(),
          message.vehicleCode());
      return;
    }
    try {
      // MqttClient.publish would wait for the broker's acknowledgement with no limit, holding the
      // request or the telemetry thread that sends the command; the wait is bounded instead.
      current
          .getTopic(message.topic())
          .publish(message.payload(json), QOS, false)
          .waitForCompletion(ACK_TIMEOUT_MS);
    } catch (MqttException failed) {
      log.error(
          "{} for vehicle {} not confirmed by the broker (reason {})",
          message.command(),
          message.vehicleCode(),
          failed.getReasonCode());
    }
  }

  // Connects once before the application takes requests, so a broker that is up is ready for the
  // first trip; otherwise keeps trying in the background.
  @Override
  public synchronized void start() {
    if (connection.isRunning()) {
      return;
    }
    connection.open();
    connection.connect();
  }

  @Override
  public void stop() {
    connection.close();
  }

  @Override
  public boolean isRunning() {
    return connection.isRunning();
  }
}
