package com.riaride.trips.internal.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// The hub the guest returns the bike to (RIA-55).
record EndTripRequest(@NotNull UUID hubId) {}
