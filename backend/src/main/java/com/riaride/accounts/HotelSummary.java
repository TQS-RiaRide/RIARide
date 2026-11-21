package com.riaride.accounts;

import java.util.UUID;

// A hotel of the group, as other modules see it.
public record HotelSummary(UUID id, String name) {
}
