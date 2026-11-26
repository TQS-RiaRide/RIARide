package com.riaride.fleet.internal.domain;

import java.util.Locale;

// The operator asked for a status the bike cannot reach from its current one (RIA-91), for
// example retiring a bike in use.
public class VehicleStatusChangeRefusedException extends RuntimeException {

  public VehicleStatusChangeRefusedException(VehicleStatus from, VehicleStatus to) {
    super("A bike that is " + describe(from) + " cannot be changed to " + describe(to) + ".");
  }

  private static String describe(VehicleStatus status) {
    return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
