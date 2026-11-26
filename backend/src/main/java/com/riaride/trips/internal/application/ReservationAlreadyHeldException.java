package com.riaride.trips.internal.application;

// BR6 and BR11: a pass holds one bike at a time, so it cannot book another while a booking is
// still held (RIA-90).
public class ReservationAlreadyHeldException extends RuntimeException {

  public ReservationAlreadyHeldException() {
    super("This pass already holds a booked bike. Start a trip on it or wait until it expires.");
  }
}
