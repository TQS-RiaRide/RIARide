package com.riaride.accounts;

import java.time.LocalDate;
import java.util.UUID;

// A guest whose stay is active today (BR1), with the stay's check-out date.
public record ActiveGuest(UUID guestId, UUID stayId, LocalDate checkOut) {
}
