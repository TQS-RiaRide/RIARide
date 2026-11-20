package com.riaride.fleet.internal.domain;

/**
 * BR12: a vehicle can only be booked when its battery is at or above the minimum. The policy only
 * decides bookability; it never changes the vehicle's lifecycle status.
 */
public record BookingBatteryPolicy(int minimumPercent) {

	public boolean allowsBooking(BatteryLevel level) {
		return level.percent() >= minimumPercent;
	}

}
