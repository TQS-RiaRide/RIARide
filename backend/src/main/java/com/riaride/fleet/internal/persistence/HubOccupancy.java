package com.riaride.fleet.internal.persistence;

import java.util.UUID;

// How many vehicles are docked at a hub, which is how many of its slots are taken (BR10).
public interface HubOccupancy {

  UUID getHubId();

  long getDocked();
}
