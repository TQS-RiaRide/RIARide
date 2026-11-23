package com.riaride.telemetry.internal.mqtt;

import com.riaride.telemetry.VehicleCommands;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
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
	private static final long RETRY_SECONDS = 5;
	private static final long DISCONNECT_TIMEOUT_MS = 2_000;

	private static final Logger log = LoggerFactory.getLogger(MqttVehicleCommandPublisher.class);

	private final TelemetryMqttProperties properties;
	private final JsonMapper json;
	private final Clock clock;
	private volatile ScheduledExecutorService connector;
	private volatile MqttClient client;
	private volatile boolean running;

	MqttVehicleCommandPublisher(TelemetryMqttProperties properties, JsonMapper json, Clock clock) {
		this.properties = properties;
		this.json = json;
		this.clock = clock;
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
		var current = client;
		if (current == null || !current.isConnected()) {
			log.warn("MQTT broker not connected; {} for vehicle {} dropped", message.command(), message.vehicleCode());
			return;
		}
		try {
			current.publish(message.topic(), message.payload(json), QOS, false);
		} catch (MqttException failed) {
			log.error("{} for vehicle {} not sent (reason {})", message.command(), message.vehicleCode(),
				failed.getReasonCode());
		}
	}

	// Connects once before the application takes requests, so a broker that is up is ready for the
	// first trip; otherwise keeps trying in the background.
	@Override
	public synchronized void start() {
		if (running) {
			return;
		}
		try {
			client = new MqttClient(properties.url(), properties.clientId() + "-commands", new MemoryPersistence());
		} catch (MqttException invalidSettings) {
			throw new IllegalStateException("Invalid MQTT broker settings", invalidSettings);
		}
		connector = Executors.newSingleThreadScheduledExecutor(runnable -> {
			var thread = new Thread(runnable, "mqtt-commands-connect");
			thread.setDaemon(true);
			return thread;
		});
		running = true;
		connect();
	}

	private void connect() {
		if (!running) {
			return;
		}
		try {
			client.connect(connectOptions());
		} catch (MqttException unreachable) {
			log.warn("MQTT broker not reachable for commands (reason {}); retrying in {} s",
				unreachable.getReasonCode(), RETRY_SECONDS);
			var executor = connector;
			if (executor != null && !executor.isShutdown()) {
				executor.schedule(this::connect, RETRY_SECONDS, TimeUnit.SECONDS);
			}
		}
	}

	private MqttConnectOptions connectOptions() {
		var options = new MqttConnectOptions();
		options.setAutomaticReconnect(true);
		if (properties.hasCredentials()) {
			options.setUserName(properties.username());
			options.setPassword(properties.password() == null ? new char[0] : properties.password().toCharArray());
		}
		return options;
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
			log.debug("MQTT command client closed with reason {}", closing.getReasonCode());
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

}
