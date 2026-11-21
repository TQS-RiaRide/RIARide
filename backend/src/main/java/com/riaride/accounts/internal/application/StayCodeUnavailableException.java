package com.riaride.accounts.internal.application;

// Every generated code was already taken. With about 10^12 codes this signals a fault,
// such as a broken random generator, not a full code space.
public class StayCodeUnavailableException extends RuntimeException {

	public StayCodeUnavailableException(int attempts) {
		super("No free stay code after " + attempts + " attempts");
	}

}
