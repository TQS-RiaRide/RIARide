package com.riaride.fleet.internal.application;

// Another vehicle already has this code. The message does not repeat the code.
public class VehicleCodeAlreadyInUseException extends RuntimeException {

	public VehicleCodeAlreadyInUseException() {
		super("Another vehicle already has this code. Choose a different code.");
	}

}
