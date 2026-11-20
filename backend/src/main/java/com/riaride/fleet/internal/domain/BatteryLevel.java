package com.riaride.fleet.internal.domain;

/** A vehicle's battery charge as a percentage, as reported by telemetry. */
public record BatteryLevel(int percent) {

	public static BatteryLevel of(int percent) {
		return new BatteryLevel(percent);
	}

}
