package com.riaride.fleet.internal.application;

public class HubNotFoundException extends RuntimeException {

	public HubNotFoundException() {
		super("No hub has this id.");
	}

}
