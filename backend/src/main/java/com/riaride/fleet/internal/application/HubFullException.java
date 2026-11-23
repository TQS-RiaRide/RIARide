package com.riaride.fleet.internal.application;

// Every slot of the hub is taken by a docked vehicle (BR10).
public class HubFullException extends RuntimeException {

	public HubFullException() {
		super("The hub has no free slot. Choose another hub or free a slot first.");
	}

}
