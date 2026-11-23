package com.riaride.trips.internal.application;

// A trip needs a paid pass that has not ended (BR2, BR4).
public class NoUsablePassException extends RuntimeException {

	public NoUsablePassException() {
		super("You need a valid pass to start a trip.");
	}

}
