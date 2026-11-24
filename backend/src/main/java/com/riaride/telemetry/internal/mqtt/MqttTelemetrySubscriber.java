package com.riaride.telemetry.internal.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

// The MQTT adapter (ADR 0006): subscribes to every vehicle's telemetry topic with QoS 1 and hands
// each message to the handler. A broker that is down at startup does not stop the application: the
// subscriber retries until it connects and subscribes, and Paho reconnects after a lost connection.
// The session is kept (clean session off), so the broker queues QoS 1 messages while the platform
// is
// away. A kept session is tied to the client id, so one backend instance runs per client id; a
// second
// instance needs its own MQTT_CLIENT_ID (or a shared subscription, once the deployment needs it).
class MqttTelemetrySubscriber implements SmartLifecycle, MqttCallbackExtended {

  static final String TELEMETRY_TOPICS = "riaride/vehicles/+/telemetry";
  private static final int QOS = 1;

  private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

  private final TelemetryMessageHandler handler;
  private final MqttConnection connection;

  MqttTelemetrySubscriber(TelemetryMqttProperties properties, TelemetryMessageHandler handler) {
    this.handler = handler;
    var options = new MqttConnectOptions();
    options.setCleanSession(false);
    this.connection =
        new MqttConnection(properties, properties.clientId(), "mqtt-telemetry-connect", options);
  }

  @Override
  public synchronized void start() {
    if (connection.isRunning()) {
      return;
    }
    connection.open(this);
    connection.execute(connection::connect);
  }

  // Called on the first connection and on every automatic reconnection, on Paho's only callback
  // thread. The blocking subscribe must not run here: with a kept session the broker sends the
  // queued messages right after CONNACK, before the SUBACK. Once Paho's inbound queue is full its
  // receiver stops reading, the SUBACK never arrives, and both threads wait forever.
  @Override
  public void connectComplete(boolean reconnect, String serverUri) {
    connection.execute(this::subscribe);
  }

  // Paho does not retry a failed subscription, so this does: a connected but unsubscribed adapter
  // would otherwise stay silent.
  private void subscribe() {
    if (!connection.isRunning()) {
      return;
    }
    try {
      connection.client().subscribe(TELEMETRY_TOPICS, QOS);
      log.info("Subscribed to {}", TELEMETRY_TOPICS);
    } catch (MqttException failed) {
      log.error(
          "Could not subscribe to {} (reason {}); retrying in {} s",
          TELEMETRY_TOPICS,
          failed.getReasonCode(),
          MqttConnection.RETRY_SECONDS);
      connection.retryLater(this::subscribe);
    }
  }

  // An exception here would make Paho drop the connection, and the broker would redeliver the same
  // message on every reconnection. A message that cannot be handled is logged, without its
  // payload, and dropped: the simulator sends each vehicle's state periodically, so the next
  // reading brings it up to date.
  @Override
  public void messageArrived(String topic, MqttMessage message) {
    try {
      handler.handle(topic, message.getPayload());
    } catch (RuntimeException failed) {
      // The exception is logged with its cause, so a database failure can be diagnosed; the
      // invalid-payload exceptions, whose messages could quote the payload, never get here.
      log.error("Telemetry message on {} dropped", LogSafeTopic.of(topic), failed);
    }
  }

  @Override
  public void connectionLost(Throwable cause) {
    log.warn("MQTT connection lost; Paho reconnects automatically");
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    // This adapter only subscribes.
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
