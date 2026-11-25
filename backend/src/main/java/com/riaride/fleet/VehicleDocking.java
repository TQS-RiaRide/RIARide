package com.riaride.fleet;

import java.util.UUID;

// Public API of the fleet module for the trips module (RIA-55). Joins the caller's transaction. The
// vehicle row is locked before the hub row: telemetry locks the vehicle before the trip, so a trip
// end that locked them the other way round could deadlock with a reading of the same bike. The hub
// lock makes two trip ends at the same hub check its free slots one after the other (BR10).
public interface VehicleDocking {

  // The bike in use is docked at the hub and is available again. Throws VehicleNotInUseException
  // when the bike is no longer in use (a concurrent end docked it first), UnknownHubException for
  // an
  // unknown hub, and HubWithoutFreeSlotException, with the nearest hubs that have a free slot, when
  // every slot of the hub is taken. None of them changes the bike.
  void dock(UUID vehicleId, UUID hubId);
}
