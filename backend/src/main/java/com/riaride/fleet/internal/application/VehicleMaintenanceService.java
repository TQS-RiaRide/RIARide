package com.riaride.fleet.internal.application;

import com.riaride.fleet.FaultyVehicle;
import com.riaride.fleet.VehicleMaintenance;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.VehicleRetiredException;
import com.riaride.fleet.internal.domain.VehicleStatusChangeRefusedException;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Takes a faulty bike out of service for the maintenance module (RIA-92). MANDATORY: the change to
// the bike is stored with the ticket, in the maintenance module's transaction, or not at all. The
// rules per lifecycle state are in Vehicle.reportFault.
@Service
class VehicleMaintenanceService implements VehicleMaintenance {

  private final VehicleRepository vehicles;

  VehicleMaintenanceService(VehicleRepository vehicles) {
    this.vehicles = vehicles;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public FaultyVehicle reportFault(String vehicleCode) {
    var vehicle =
        vehicles.findByCodeForUpdate(vehicleCode).orElseThrow(VehicleNotFoundException::new);
    try {
      return new FaultyVehicle(vehicle.getId(), vehicle.getCode(), vehicle.reportFault());
    } catch (VehicleStatusChangeRefusedException refused) {
      // Only a retired bike refuses a fault; the maintenance module sees public types only.
      throw new VehicleRetiredException();
    }
  }
}
