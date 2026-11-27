package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Changes how many bikes a hub takes (RIA-96, BR10). The hub row is locked before its docked bikes
// are counted, as when a trip ends there (VehicleDockingService), so a bike docked at the same time
// is counted either before the change or against the new capacity, never against neither. A
// capacity below the bikes already docked is refused: they would stay over the limit. Equal to them
// is allowed, and the hub is then full: the next trip that tries to end there is refused.
@Service
public class ChangeHubCapacityService {

  private final HubRepository hubs;
  private final VehicleRepository vehicles;

  public ChangeHubCapacityService(HubRepository hubs, VehicleRepository vehicles) {
    this.hubs = hubs;
    this.vehicles = vehicles;
  }

  @Transactional
  public HubCapacity changeCapacity(UUID hubId, int capacity, HubEditor editor) {
    // An invalid value is refused before the hub is read or locked.
    Hub.requireValidCapacity(capacity);
    var hub = hubs.findByIdForUpdate(hubId).orElseThrow(HubNotFoundException::new);
    if (!editor.canEdit(hub)) {
      throw new HubAccessDeniedException();
    }
    var docked = vehicles.countByHubId(hub.getId());
    if (capacity < docked) {
      throw new CapacityBelowDockedBikesException(docked);
    }
    hub.changeCapacity(capacity);
    return new HubCapacity(hub, docked);
  }
}
