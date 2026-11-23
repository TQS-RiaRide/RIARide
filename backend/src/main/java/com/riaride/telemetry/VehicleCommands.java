package com.riaride.telemetry;

// Commands the platform sends to a vehicle through the simulator's integration interface (ADR 0006).
// Fire and forget: the vehicle answers with telemetry, which is how an unlock is confirmed. A command
// that cannot be sent is logged and dropped, never thrown, so a trip whose unlock is lost times out.
public interface VehicleCommands {

	void unlock(String vehicleCode);

}
