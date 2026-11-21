package com.riaride.fleet.internal.application;

// Another hub already has this name (exact match). The message does not repeat the name.
public class HubNameAlreadyInUseException extends RuntimeException {

	public HubNameAlreadyInUseException() {
		super("Another hub already has this name. Choose a different name.");
	}

}
