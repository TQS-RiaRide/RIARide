package com.riaride.telemetry.internal.mqtt;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Connection to the simulator's MQTT broker (ADR 0006).
@ConfigurationProperties("riaride.telemetry.mqtt")
@Validated
record TelemetryMqttProperties(boolean enabled, @NotBlank String url, @NotBlank String clientId) {
}
