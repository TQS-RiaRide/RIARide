package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayStatus;
import java.time.LocalDate;
import java.util.UUID;

record StayResponse(
    UUID id,
    UUID hotelId,
    String guestName,
    String stayCode,
    LocalDate checkIn,
    LocalDate checkOut,
    StayStatus status) {

  static StayResponse from(Stay stay) {
    return new StayResponse(
        stay.getId(),
        stay.getHotelId(),
        stay.getGuestName(),
        stay.getStayCode().value(),
        stay.getCheckIn(),
        stay.getCheckOut(),
        stay.getStatus());
  }
}
