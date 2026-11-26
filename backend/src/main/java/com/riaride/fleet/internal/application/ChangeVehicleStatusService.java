package com.riaride.fleet.internal.application;

import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// An operator activates, deactivates or retires a bike (RIA-91). The rules are in
// Vehicle.changeStatusAsOperator.
@Service
public class ChangeVehicleStatusService {

  private final VehicleRepository vehicles;

  public ChangeVehicleStatusService(VehicleRepository vehicles) {
    this.vehicles = vehicles;
  }

  // The vehicle row is locked until the transaction ends, so a guest cannot reserve the bike
  // between the status check and the change: one waits for the other and then sees its result.
  @Transactional
  public Vehicle change(UUID vehicleId, VehicleStatus target) {
    var vehicle = vehicles.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
    vehicle.changeStatusAsOperator(target);
    return vehicle;
  }
}
