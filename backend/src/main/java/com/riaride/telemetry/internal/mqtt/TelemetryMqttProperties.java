package com.riaride.telemetry.internal.mqtt;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Connection to the simulator's MQTT broker (ADR 0006). The username and password are optional:
// a broker that requires them (with ACLs that let this client only subscribe to telemetry and
// publish commands) gets them from MQTT_USERNAME and MQTT_PASSWORD, never from the repository.
@ConfigurationProperties("riaride.telemetry.mqtt")
@Validated
record TelemetryMqttProperties(
    boolean enabled,
    @NotBlank String url,
    @NotBlank String clientId,
    String username,
    String password) {

  boolean hasCredentials() {
    return username != null && !username.isBlank();
  }
}
