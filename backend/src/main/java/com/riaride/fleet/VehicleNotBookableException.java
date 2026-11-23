package com.riaride.fleet;

// The vehicle cannot be booked now: it is not available, or its battery is below the minimum (BR12).
public class VehicleNotBookableException extends RuntimeException {

	public VehicleNotBookableException() {
		super("This bike cannot be booked now.");
	}

}
