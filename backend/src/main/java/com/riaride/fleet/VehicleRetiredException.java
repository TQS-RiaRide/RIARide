package com.riaride.fleet;

// The bike was retired: it never returns to service, so no fault can be reported on it (RIA-92).
public class VehicleRetiredException extends RuntimeException {

  public VehicleRetiredException() {
    super("The bike is retired and never returns to service.");
  }
}
