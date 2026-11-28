package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Hub;

// A hub as the hub list shows it: the name of its hotel (null for a tourist spot) and the bikes
// docked there now.
public record ListedHub(Hub hub, String hotelName, long dockedBikes) {}
