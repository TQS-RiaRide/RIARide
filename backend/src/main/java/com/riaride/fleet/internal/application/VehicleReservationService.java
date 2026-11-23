package com.riaride.fleet.internal.application;

import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.VehicleReservations;
import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
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
		var vehicle = vehicles.findByCodeForUpdate(vehicleCode).orElseThrow(VehicleNotFoundException::new);
		vehicle.reserve(policy);
		return new ReservedVehicle(vehicle.getId(), vehicle.getCode(), vehicle.getHubId());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void startRide(UUID vehicleId) {
		var vehicle = vehicles.findByIdForUpdate(vehicleId)
			.orElseThrow(() -> new IllegalStateException("A reserved vehicle no longer exists"));
		vehicle.startRide();
	}

}
