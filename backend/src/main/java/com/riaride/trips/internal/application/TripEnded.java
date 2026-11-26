package com.riaride.trips.internal.application;

import java.util.UUID;

// A trip ended at a hub (RIA-55): once the transaction commits, the bike is sent LOCK at that hub.
// Story 2.3 charges the overtime from it.
record TripEnded(UUID tripId, String vehicleCode, UUID hubId) {}
