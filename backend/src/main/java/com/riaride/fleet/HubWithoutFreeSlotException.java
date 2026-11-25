package com.riaride.fleet;

import java.util.List;

// Every slot of the hub is taken, so a trip cannot end there (BR10). Carries the nearest hubs that
// have a free slot, nearest first; the list is empty when no other hub has one.
public class HubWithoutFreeSlotException extends RuntimeException {

  private final transient List<NearbyHub> nearbyHubs;

  public HubWithoutFreeSlotException(List<NearbyHub> nearbyHubs) {
    super("The hub has no free slot. End the trip at one of the nearby hubs.");
    this.nearbyHubs = List.copyOf(nearbyHubs);
  }

  public List<NearbyHub> nearbyHubs() {
    return nearbyHubs;
  }
}
