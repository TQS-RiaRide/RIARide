package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.ListedHub;
import com.riaride.fleet.internal.application.RegisteredHub;
import com.riaride.fleet.internal.domain.HubKind;
import java.util.UUID;

// hotel is the hotel's name, or null for a tourist spot hub. freeSlots is capacity minus the bikes
// docked there.
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

  // A hub of the list (RIA-107), with the bikes docked there now.
  static HubResponse from(ListedHub listed) {
    var hub = listed.hub();
    var docked = (int) listed.dockedBikes();
    return new HubResponse(
        hub.getId(),
        hub.getName(),
        hub.getKind(),
        listed.hotelName(),
        hub.getLatitude(),
        hub.getLongitude(),
        hub.getCapacity(),
        docked,
        hub.getCapacity() - docked);
  }
}
