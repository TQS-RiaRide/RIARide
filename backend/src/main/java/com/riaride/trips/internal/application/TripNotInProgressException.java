package com.riaride.trips.internal.application;

// Only a trip in use can end (RIA-55): one still starting, failed or already ended cannot.
public class TripNotInProgressException extends RuntimeException {

  public TripNotInProgressException() {
    super("This trip is not in progress, so it cannot be ended.");
  }
}
