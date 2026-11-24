package com.riaride.telemetry;

import java.time.Instant;

// A telemetry reading for one vehicle, as the simulator reported it. The fleet module keeps the
// latest vehicle state from it (architecture, section 5). hubId is null while the vehicle is
// ridden.
public record VehicleTelemetryReceived(
    String vehicleCode,
    String messageId,
    Instant emittedAt,
    int batteryPct,
    String hubId,
    String lockState) {}
