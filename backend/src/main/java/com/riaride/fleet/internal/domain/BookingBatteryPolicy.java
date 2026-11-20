package com.riaride.fleet.internal.domain;

/**
 * BR12: a vehicle can only be booked when its battery is at or above the minimum. The policy only
 * decides bookability; it never changes the vehicle's lifecycle status.
 */
public record BookingBatteryPolicy(int minimumPercent) {

	/**
	 * Returns whether a vehicle with this battery level can be booked. A vehicle with no battery
	 * reading yet (no telemetry received) cannot be booked.
	 */
	public boolean allowsBooking(BatteryLevel level) {
		return level != null && level.percent() >= minimumPercent;
	}

}
