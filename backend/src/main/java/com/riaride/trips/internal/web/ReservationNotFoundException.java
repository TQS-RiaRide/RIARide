package com.riaride.trips.internal.web;

// No booking with this id belongs to the guest's pass (RIA-90).
class ReservationNotFoundException extends RuntimeException {

  ReservationNotFoundException() {
    super("No booking of yours has this id.");
  }
}
