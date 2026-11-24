package com.riaride.trips.internal.application;

// After a failed start the pass still holds its bike until the reservation expires (BR11, BR13).
public class ReservationHeldForAnotherBikeException extends RuntimeException {

  public ReservationHeldForAnotherBikeException() {
    super("Your pass holds another bike until its reservation expires.");
  }
}
