package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The vehicles docked at a hub, each marked bookable or not (BR12, RIA-59), optionally only those
// of one type, so a guest finds the bikes to book by hub and type (RIA-53).
@Service
public class ListHubVehiclesService {

	private final HubRepository hubs;
	private final VehicleRepository vehicles;
	private final BookingBatteryPolicy policy;

	public ListHubVehiclesService(HubRepository hubs, VehicleRepository vehicles, BookingBatteryPolicy policy) {
		this.hubs = hubs;
		this.vehicles = vehicles;
		this.policy = policy;
	}

	@Transactional(readOnly = true)
	public List<HubVehicle> list(UUID hubId) {
		return list(hubId, null);
	}

	// type null lists every type.
	@Transactional(readOnly = true)
	public List<HubVehicle> list(UUID hubId, VehicleType type) {
		if (!hubs.existsById(hubId)) {
			throw new HubNotFoundException();
		}
		return vehicles.findByHubIdOrderByCode(hubId).stream()
			.filter(vehicle -> type == null || vehicle.getType() == type)
			.map(vehicle -> HubVehicle.of(vehicle, vehicle.isBookable(policy)))
			.toList();
	}

}
