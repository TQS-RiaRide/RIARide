package com.riaride.fleet.internal.persistence;

import com.riaride.fleet.internal.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

	// The code is the id the simulator uses in its MQTT topics.
	Optional<Vehicle> findByCode(String code);

	List<Vehicle> findByHubIdOrderByCode(UUID hubId);

	// Codes are unique regardless of case, so EB-401 and eb-401 cannot both exist (V6).
	boolean existsByCodeIgnoreCase(String code);

	// The vehicles docked at a hub, which take its slots (BR10). A ridden vehicle has no hub.
	long countByHubId(UUID hubId);

}
