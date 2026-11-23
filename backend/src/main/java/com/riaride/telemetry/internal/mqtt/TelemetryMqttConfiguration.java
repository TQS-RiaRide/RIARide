package com.riaride.telemetry.internal.mqtt;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

// Wires the MQTT adapter. With riaride.telemetry.mqtt.enabled=false (most tests) nothing connects.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelemetryMqttProperties.class)
@ConditionalOnBooleanProperty("riaride.telemetry.mqtt.enabled")
class TelemetryMqttConfiguration {

	@Bean
	TelemetryMessageHandler telemetryMessageHandler(JsonMapper json, ApplicationEventPublisher events) {
		return new TelemetryMessageHandler(new TelemetryMessageMapper(json), events);
	}

	@Bean
	MqttTelemetrySubscriber mqttTelemetrySubscriber(TelemetryMqttProperties properties,
		TelemetryMessageHandler handler) {
		return new MqttTelemetrySubscriber(properties, handler);
	}

	@Bean
	MqttVehicleCommandPublisher mqttVehicleCommandPublisher(TelemetryMqttProperties properties, JsonMapper json,
		Clock clock) {
		return new MqttVehicleCommandPublisher(properties, json, clock);
	}

}
