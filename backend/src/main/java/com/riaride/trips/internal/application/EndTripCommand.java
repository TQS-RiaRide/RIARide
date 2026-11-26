package com.riaride.trips.internal.application;

import java.util.UUID;

// The guest identified by keycloakId ends their trip at the hub (RIA-55).
public record EndTripCommand(String keycloakId, UUID tripId, UUID hubId) {}
