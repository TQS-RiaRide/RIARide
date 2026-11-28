package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.HubCapacity;
import java.util.UUID;

record HubCapacityResponse(UUID id, String name, int capacity, long dockedBikes, long freeSlots) {

  static HubCapacityResponse from(HubCapacity changed) {
    var hub = changed.hub();
    return new HubCapacityResponse(
        hub.getId(),
        hub.getName(),
        hub.getCapacity(),
        changed.dockedBikes(),
        hub.getCapacity() - changed.dockedBikes());
  }
}
