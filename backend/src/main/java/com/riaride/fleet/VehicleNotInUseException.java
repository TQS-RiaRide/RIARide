package com.riaride.fleet;

// The bike is not in use, so it has no ride to end (RIA-55): for example, a concurrent end of the
// same trip docked it first.
public class VehicleNotInUseException extends RuntimeException {

  public VehicleNotInUseException() {
    super("This bike is not in use.");
  }
}
