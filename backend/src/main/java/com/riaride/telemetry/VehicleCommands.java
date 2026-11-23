package com.riaride.telemetry;

// Commands the platform sends to a vehicle through the simulator's integration interface (ADR 0006),
// with QoS 1. Fire and forget: the vehicle answers with telemetry, which is how an unlock is
// confirmed. A command that cannot be sent is logged and dropped, never thrown, so a trip whose
// unlock is lost times out.
public interface VehicleCommands {

	void unlock(String vehicleCode);

	// hubId is the hub the vehicle locks at, or null to lock it where it is.
	void lock(String vehicleCode, String hubId);

}
