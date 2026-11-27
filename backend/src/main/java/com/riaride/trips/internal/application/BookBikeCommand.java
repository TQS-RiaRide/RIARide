package com.riaride.trips.internal.application;

// The guest identified by keycloakId books the bike with this code (RIA-90).
public record BookBikeCommand(String keycloakId, String vehicleCode) {}
