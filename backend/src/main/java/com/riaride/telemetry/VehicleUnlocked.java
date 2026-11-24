package com.riaride.telemetry;

import java.time.Instant;

// The simulator reported a vehicle as unlocked (RIA-54). Published in the same transaction as the
// VehicleTelemetryReceived event of the same reading, so a starting trip is confirmed before the
// MQTT message is acknowledged. The simulator keeps reporting UNLOCKED while the bike is ridden, so
// listeners must treat repeats as normal.
public record VehicleUnlocked(String vehicleCode, String messageId, Instant emittedAt) {}
