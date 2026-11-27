package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Hub;

// A hub after its capacity changed, with the bikes docked there when it did.
public record HubCapacity(Hub hub, long dockedBikes) {}
