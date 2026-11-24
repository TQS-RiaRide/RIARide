package com.riaride.telemetry.internal.mqtt;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// One MQTT client of the adapter, shared by the telemetry subscriber and the command publisher: it
// opens the client, connects with the configured credentials, retries in the background every 5 s
// while the broker is unreachable (Paho reconnects on its own once connected), and closes on stop.
final class MqttConnection {

  static final long RETRY_SECONDS = 5;
  private static final long DISCONNECT_TIMEOUT_MS = 2_000;

  private static final Logger log = LoggerFactory.getLogger(MqttConnection.class);

  private final TelemetryMqttProperties properties;
  private final String clientId;
  private final String threadName;
  private final MqttConnectOptions options;
  private volatile ScheduledExecutorService connector;
  private volatile MqttClient client;
  private volatile boolean running;

  // options holds the adapter's own settings; credentials and automatic reconnection are added.
  MqttConnection(
      TelemetryMqttProperties properties,
      String clientId,
      String threadName,
      MqttConnectOptions options) {
    this.properties = properties;
    this.clientId = clientId;
    this.threadName = threadName;
    this.options = withCredentials(options, properties);
  }

  private static MqttConnectOptions withCredentials(
      MqttConnectOptions options, TelemetryMqttProperties properties) {
    options.setAutomaticReconnect(true);
    if (properties.hasCredentials()) {
      options.setUserName(properties.username());
      options.setPassword(
          properties.password() == null ? new char[0] : properties.password().toCharArray());
    }
    return options;
  }

  // Creates the client, which this connection owns and closes on stop.
  void open() {
    open(null);
  }

  // Creates the client with its callback set before connecting, so no message arrives without it.
  synchronized void open(MqttCallback callback) {
    try {
      client = new MqttClient(properties.url(), clientId, new MemoryPersistence());
    } catch (MqttException invalidSettings) {
      throw new IllegalStateException("Invalid MQTT broker settings", invalidSettings);
    }
    if (callback != null) {
      client.setCallback(callback);
    }
    connector =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          var thread = new Thread(runnable, threadName);
          thread.setDaemon(true);
          return thread;
        });
    running = true;
  }

  // Tries once on the caller's thread; while the broker is unreachable, retries in the background.
  void connect() {
    if (!running) {
      return;
    }
    try {
      client.connect(options);
    } catch (MqttException unreachable) {
      log.warn(
          "MQTT broker not reachable for {} (reason {}); retrying in {} s",
          clientId,
          unreachable.getReasonCode(),
          RETRY_SECONDS);
      retryLater(this::connect);
    }
  }

  // Runs the action on the connection's own thread, never on Paho's callback thread.
  void execute(Runnable action) {
    var executor = connector;
    if (running && executor != null && !executor.isShutdown()) {
      executor.execute(action);
    }
  }

  void retryLater(Runnable action) {
    var executor = connector;
    if (running && executor != null && !executor.isShutdown()) {
      executor.schedule(action, RETRY_SECONDS, TimeUnit.SECONDS);
    }
  }

  MqttClient client() {
    return client;
  }

  boolean isRunning() {
    return running;
  }

  // A client that fails to close is only logged: the application is stopping.
  synchronized void close() {
    running = false;
    if (connector != null) {
      connector.shutdownNow();
    }
    if (client == null) {
      return;
    }
    try {
      if (client.isConnected()) {
        client.disconnectForcibly(DISCONNECT_TIMEOUT_MS);
      }
      client.close(true);
    } catch (MqttException closing) {
      log.debug("MQTT client {} closed with reason {}", clientId, closing.getReasonCode());
    }
  }
}
