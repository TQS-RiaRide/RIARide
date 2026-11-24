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
// subscriber retries until it connects and subscribes, and Paho reconnects after a lost connection.
// The session is kept (clean session off), so the broker queues QoS 1 messages while the platform is
// away. A kept session is tied to the client id, so one backend instance runs per client id; a second
// instance needs its own MQTT_CLIENT_ID (or a shared subscription, once the deployment needs it).
class MqttTelemetrySubscriber implements SmartLifecycle, MqttCallbackExtended {

	static final String TELEMETRY_TOPICS = "riaride/vehicles/+/telemetry";
	private static final int QOS = 1;
	private static final long RETRY_SECONDS = 5;
	private static final long DISCONNECT_TIMEOUT_MS = 2_000;

	private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

	private final TelemetryMqttProperties properties;
	private final TelemetryMessageHandler handler;
	private volatile ScheduledExecutorService connector;
	private volatile MqttClient client;
	private volatile boolean running;

	MqttTelemetrySubscriber(TelemetryMqttProperties properties, TelemetryMessageHandler handler) {
		this.properties = properties;
		this.handler = handler;
	}

	@Override
	public synchronized void start() {
		if (running) {
			return;
		}
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
		try {
			client.connect(connectOptions());
		} catch (MqttException unreachable) {
			log.warn("MQTT broker not reachable (reason {}); retrying in {} s", unreachable.getReasonCode(),
				RETRY_SECONDS);
			retryLater(this::connect);
		}
	}

	private MqttConnectOptions connectOptions() {
		var options = new MqttConnectOptions();
		options.setCleanSession(false);
		options.setAutomaticReconnect(true);
		if (properties.hasCredentials()) {
			options.setUserName(properties.username());
			options.setPassword(properties.password() == null ? new char[0] : properties.password().toCharArray());
		}
		return options;
	}

	// Called on the first connection and on every automatic reconnection, on Paho's only callback
	// thread. The blocking subscribe must not run here: with a kept session the broker sends the
	// queued messages right after CONNACK, before the SUBACK. Once Paho's inbound queue is full its
	// receiver stops reading, the SUBACK never arrives, and both threads wait forever.
	@Override
	public void connectComplete(boolean reconnect, String serverUri) {
		var executor = connector;
		if (running && executor != null && !executor.isShutdown()) {
			executor.execute(this::subscribe);
		}
	}

	// Paho does not retry a failed subscription, so this does: a connected but unsubscribed adapter
	// would otherwise stay silent.
	private void subscribe() {
		if (!running) {
			return;
		}
		try {
			client.subscribe(TELEMETRY_TOPICS, QOS);
			log.info("Subscribed to {}", TELEMETRY_TOPICS);
		} catch (MqttException failed) {
			log.error("Could not subscribe to {} (reason {}); retrying in {} s", TELEMETRY_TOPICS,
				failed.getReasonCode(), RETRY_SECONDS);
			retryLater(this::subscribe);
		}
	}

	private void retryLater(Runnable action) {
		var executor = connector;
		if (running && executor != null && !executor.isShutdown()) {
			executor.schedule(action, RETRY_SECONDS, TimeUnit.SECONDS);
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
			log.error("Telemetry message dropped: {}", failed.getClass().getSimpleName());
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
	public synchronized void stop() {
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
			log.debug("MQTT client closed with reason {}", closing.getReasonCode());
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

}
