package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.HubKind;

// hotelName is the name of the hotel a HOTEL hub belongs to, and null for a TOURIST_SPOT hub.
public record RegisterHubCommand(
    String name, HubKind kind, String hotelName, double latitude, double longitude, int capacity) {}
