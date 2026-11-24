// Telemetry: receives each vehicle's readings from the simulator through one integration interface
// (MQTT, ADR 0006) and publishes them as events. Replacing the simulator means replacing the adapter
// in internal.mqtt, with no change to other modules.
// Only this package is public; everything under internal is the module's own.
@org.springframework.modulith.ApplicationModule(displayName = "Telemetry")
package com.riaride.telemetry;
