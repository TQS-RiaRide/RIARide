package com.riaride.fleet.internal.application;

import static java.util.stream.Collectors.toMap;

import com.riaride.fleet.HubWithoutFreeSlotException;
import com.riaride.fleet.NearbyHub;
import com.riaride.fleet.UnknownHubException;
import com.riaride.fleet.VehicleDocking;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.persistence.HubOccupancy;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Docks the bike of a trip that ends (RIA-55, BR10). MANDATORY: the bike is docked in the trips
// module's transaction, with the trip ended, or not at all. The vehicle row is locked first and the
// hub row second (see VehicleDocking), then the hub's docked vehicles are counted.
//
// A full hub suggests the nearest hubs with a free slot, by straight-line distance from it
// (architecture, section 7). The suggestions are read without locks: they are advice, and the
// guest's next request checks the chosen hub again.
@Service
class VehicleDockingService implements VehicleDocking {

  // How many hubs a full hub suggests.
  static final int MAX_SUGGESTIONS = 3;

  private final VehicleRepository vehicles;
  private final HubRepository hubs;

  VehicleDockingService(VehicleRepository vehicles, HubRepository hubs) {
    this.vehicles = vehicles;
    this.hubs = hubs;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void dock(UUID vehicleId, UUID hubId) {
    var vehicle =
        vehicles
            .findByIdForUpdate(vehicleId)
            .orElseThrow(() -> new IllegalStateException("The trip's bike does not exist"));
    var hub = hubs.findByIdForUpdate(hubId).orElseThrow(UnknownHubException::new);
    if (vehicles.countByHubId(hub.getId()) >= hub.getCapacity()) {
      throw new HubWithoutFreeSlotException(nearestWithFreeSlot(hub));
    }
    vehicle.endRide(hub.getId());
  }

  // Ties in distance are ordered by name, so the answer does not depend on the database's order.
  private List<NearbyHub> nearestWithFreeSlot(Hub full) {
    var docked =
        vehicles.countDockedByHub().stream()
            .collect(toMap(HubOccupancy::getHubId, HubOccupancy::getDocked));
    return hubs.findAll().stream()
        .filter(hub -> !hub.getId().equals(full.getId()))
        .filter(hub -> hub.getCapacity() > docked.getOrDefault(hub.getId(), 0L))
        .sorted(
            Comparator.comparingDouble((Hub hub) -> full.metersTo(hub)).thenComparing(Hub::getName))
        .limit(MAX_SUGGESTIONS)
        .map(hub ->
            new NearbyHub(
                hub.getId(),
                hub.getName(),
                (int) (hub.getCapacity() - docked.getOrDefault(hub.getId(), 0L)),
                Math.round(full.metersTo(hub))))
        .toList();
  }
}
