package com.riaride.accounts.internal.application;

import java.util.UUID;

public class HotelNotFoundException extends RuntimeException {

  public HotelNotFoundException(UUID hotelId) {
    super("Hotel " + hotelId + " does not exist");
  }
}
