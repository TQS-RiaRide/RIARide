package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.RegisteredHub;
import com.riaride.fleet.internal.domain.HubKind;
import java.util.UUID;

// hotel is the hotel's name, or null for a tourist spot hub. Only built for a hub that was just
// registered, which has no bikes docked yet; a response for an existing hub must count its
// vehicles (VehicleRepository.countByHubId).
record HubResponse(
    UUID id,
    String name,
    HubKind kind,
    String hotel,
    double latitude,
    double longitude,
    int capacity,
    int dockedBikes,
    int freeSlots) {

  static HubResponse from(RegisteredHub registered) {
    var hub = registered.hub();
    return new HubResponse(
        hub.getId(),
        hub.getName(),
        hub.getKind(),
        registered.hotelName(),
        hub.getLatitude(),
        hub.getLongitude(),
        hub.getCapacity(),
        0,
        hub.getCapacity());
  }
}
