package com.riaride.accounts.internal.domain;

// A stay lasts at least one night: check-out must be after check-in.
public class InvalidStayDatesException extends RuntimeException {

  public InvalidStayDatesException() {
    super("Check-out must be after check-in");
  }
}
