package com.riaride.trips.internal.application;

// BR6: a pass has one trip in progress at a time.
public class TripAlreadyInProgressException extends RuntimeException {

  public TripAlreadyInProgressException() {
    super("This pass already has a trip in progress.");
  }
}
