package com.riaride.fleet.internal.application;

import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.VehicleReservations;
import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Reserves a bike and starts its ride for the trips module (RIA-54). MANDATORY: the change to the
// bike is stored with the trip, in the trips module's transaction, or not at all.
@Service
class VehicleReservationService implements VehicleReservations {

  private final VehicleRepository vehicles;
  private final BookingBatteryPolicy policy;

  VehicleReservationService(VehicleRepository vehicles, BookingBatteryPolicy policy) {
    this.vehicles = vehicles;
    this.policy = policy;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ReservedVehicle reserve(String vehicleCode) {
    var vehicle =
        vehicles.findByCodeForUpdate(vehicleCode).orElseThrow(VehicleNotFoundException::new);
    vehicle.reserve(policy);
    return new ReservedVehicle(vehicle.getId(), vehicle.getCode(), vehicle.getHubId());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean isReserved(UUID vehicleId) {
    return vehicles
        .findByIdForUpdate(vehicleId)
        .map(vehicle -> vehicle.getStatus() == VehicleStatus.RESERVED)
        .orElse(false);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean startRide(UUID vehicleId) {
    var vehicle = vehicles.findByIdForUpdate(vehicleId);
    if (vehicle.isEmpty() || vehicle.get().getStatus() != VehicleStatus.RESERVED) {
      return false;
    }
    vehicle.get().startRide();
    return true;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void release(UUID vehicleId) {
    vehicles.findByIdForUpdate(vehicleId).ifPresent(Vehicle::release);
  }
}
