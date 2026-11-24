package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Registers a vehicle docked at a hub, as available (RIA-57). BR10: a hub holds at most its
// capacity, so a vehicle is refused at a hub with no free slot.
@Service
public class RegisterVehicleService {

  private final HubRepository hubs;
  private final VehicleRepository vehicles;

  public RegisterVehicleService(HubRepository hubs, VehicleRepository vehicles) {
    this.hubs = hubs;
    this.vehicles = vehicles;
  }

  // The hub row is locked before its vehicles are counted, so two registrations at a hub with one
  // free slot cannot both take it. The unique constraint on vehicle.code is the final guard
  // against a concurrent duplicate code: the insert is flushed here, so its
  // DataIntegrityViolationException reaches the caller.
  @Transactional
  public Vehicle register(RegisterVehicleCommand command) {
    var vehicle = Vehicle.register(command.code(), command.type(), command.hubId());
    var hub = hubs.findByIdForUpdate(command.hubId()).orElseThrow(HubNotFoundException::new);
    if (vehicles.existsByCode(vehicle.getCode())) {
      throw new VehicleCodeAlreadyInUseException();
    }
    if (vehicles.countByHubId(hub.getId()) >= hub.getCapacity()) {
      throw new HubFullException();
    }
    return vehicles.saveAndFlush(vehicle);
  }
}
