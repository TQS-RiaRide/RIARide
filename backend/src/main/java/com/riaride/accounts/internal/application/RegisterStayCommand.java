package com.riaride.accounts.internal.application;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterStayCommand(
    UUID hotelId, String guestName, LocalDate checkIn, LocalDate checkOut) {}
