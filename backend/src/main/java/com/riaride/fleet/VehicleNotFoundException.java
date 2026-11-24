package com.riaride.fleet;

public class VehicleNotFoundException extends RuntimeException {

  public VehicleNotFoundException() {
    super("No bike has this code.");
  }
}
