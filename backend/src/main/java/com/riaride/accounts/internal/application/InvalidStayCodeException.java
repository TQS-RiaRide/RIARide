package com.riaride.accounts.internal.application;

// The same error for a malformed, unknown or already used stay code,
// so the response never reveals whether a code exists.
public class InvalidStayCodeException extends RuntimeException {

	public InvalidStayCodeException() {
		super("The stay code is not valid. Check the code the hotel gave you at check-in.");
	}

}
