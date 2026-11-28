package com.riaride.fleet;

// Public API of the fleet module for the maintenance module (RIA-92). Joins the caller's
// transaction, so the bike's new state is stored with the ticket or not at all. The vehicle row is
// locked, so a guest cannot book the bike between the check and the change.
public interface VehicleMaintenance {

  // A fault was reported on the bike: it leaves service, or, if it is in use, when its trip ends.
  // The code is compared regardless of case. Throws VehicleNotFoundException for an unknown code
  // and VehicleRetiredException for a retired bike; neither changes the bike.
  FaultyVehicle reportFault(String vehicleCode);
}
