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
}
