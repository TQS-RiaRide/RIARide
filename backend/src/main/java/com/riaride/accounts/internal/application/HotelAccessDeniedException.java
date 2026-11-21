package com.riaride.accounts.internal.application;

public class HotelAccessDeniedException extends RuntimeException {

	public HotelAccessDeniedException() {
		super("You can only register stays at the hotel you manage");
	}

}
