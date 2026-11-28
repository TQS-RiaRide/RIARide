package com.riaride.trips.internal.application;

import java.util.UUID;

// A trip ended at a hub (RIA-55): once the transaction commits, the bike is sent LOCK at that hub
// and its overtime is charged (RIA-88).
record TripEnded(UUID tripId, String vehicleCode, UUID hubId) {}
