package com.riaride.telemetry.internal.mqtt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// With riaride.telemetry.mqtt.enabled=false, commands are dropped, so the modules that send them
// still start.
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(value = "riaride.telemetry.mqtt.enabled", havingValue = false)
class DisabledMqttConfiguration {

	@Bean
	DisabledVehicleCommands disabledVehicleCommands() {
		return new DisabledVehicleCommands();
	}

}
