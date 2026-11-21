package com.riaride.telemetry.internal.mqtt;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

// The MQTT adapter (ADR 0006): subscribes to every vehicle's telemetry topic with QoS 1 and hands
// each message to the handler. A broker that is down at startup does not stop the application: the
// subscriber retries until it connects, and Paho reconnects after a lost connection. The session is
// kept (clean session off), so the broker queues QoS 1 messages while the platform is away.
class MqttTelemetrySubscriber implements SmartLifecycle, MqttCallbackExtended {

	static final String TELEMETRY_TOPICS = "riaride/vehicles/+/telemetry";
	private static final int QOS = 1;
	private static final long RETRY_SECONDS = 5;

	private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

	private final TelemetryMqttProperties properties;
	private final TelemetryMessageHandler handler;
	private ScheduledExecutorService connector;
	private MqttClient client;
	private volatile boolean running;

	MqttTelemetrySubscriber(TelemetryMqttProperties properties, TelemetryMessageHandler handler) {
		this.properties = properties;
		this.handler = handler;
	}

	@Override
	public void start() {
		try {
			client = new MqttClient(properties.url(), properties.clientId(), new MemoryPersistence());
		} catch (MqttException invalidSettings) {
			throw new IllegalStateException("Invalid MQTT broker settings", invalidSettings);
		}
		client.setCallback(this);
		connector = Executors.newSingleThreadScheduledExecutor(runnable -> {
			var thread = new Thread(runnable, "mqtt-telemetry-connect");
			thread.setDaemon(true);
			return thread;
		});
		running = true;
		connector.execute(this::connect);
	}

	private void connect() {
		if (!running) {
			return;
		}
		var options = new MqttConnectOptions();
		options.setCleanSession(false);
		options.setAutomaticReconnect(true);
		if (properties.hasCredentials()) {
			options.setUserName(properties.username());
			options.setPassword(properties.password() == null ? new char[0] : properties.password().toCharArray());
		}
		try {
			client.connect(options);
		} catch (MqttException unreachable) {
			log.warn("MQTT broker {} not reachable ({}); retrying in {} s", properties.url(),
				unreachable.getReasonCode(), RETRY_SECONDS);
			connector.schedule(this::connect, RETRY_SECONDS, TimeUnit.SECONDS);
		}
	}

	// Called on the first connection and on every automatic reconnection.
	@Override
	public void connectComplete(boolean reconnect, String serverUri) {
		try {
			client.subscribe(TELEMETRY_TOPICS, QOS);
			log.info("Subscribed to {} on {}", TELEMETRY_TOPICS, serverUri);
		} catch (MqttException failed) {
			log.error("Could not subscribe to {} ({})", TELEMETRY_TOPICS, failed.getReasonCode());
		}
	}

	// An exception here would make Paho drop the connection, and the broker would redeliver the same
	// message on every reconnection. A message that cannot be handled is logged, without its
	// payload, and dropped; the next reading of the same vehicle brings the state up to date.
	@Override
	public void messageArrived(String topic, MqttMessage message) {
		try {
			handler.handle(topic, message.getPayload());
		} catch (RuntimeException failed) {
			log.error("Telemetry message on {} dropped: {}", topic, failed.getClass().getSimpleName());
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
		running = false;
		connector.shutdownNow();
		try {
			if (client.isConnected()) {
				client.disconnect();
			}
			client.close();
		} catch (MqttException ignored) {
			log.debug("MQTT client closed with reason {}", ignored.getReasonCode());
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

}
