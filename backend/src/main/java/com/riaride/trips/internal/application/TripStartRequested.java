package com.riaride.trips.internal.application;

// A trip was stored as STARTING: once its transaction commits, the bike is sent UNLOCK.
record TripStartRequested(String vehicleCode) {
}
