package com.riaride.trips.internal.application;

// BR1: only guests with an active stay can ride.
public class NoActiveStayException extends RuntimeException {

	public NoActiveStayException() {
		super("Only guests with an active stay can start a trip.");
	}

}
