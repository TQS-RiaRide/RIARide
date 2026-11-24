package com.riaride.accounts.internal.domain;

// A stay whose check-out date is before today has ended; its guest could no longer sign up or ride.
public class StayAlreadyEndedException extends RuntimeException {

  public StayAlreadyEndedException() {
    super("The stay has already ended: its check-out date is before today");
  }
}
