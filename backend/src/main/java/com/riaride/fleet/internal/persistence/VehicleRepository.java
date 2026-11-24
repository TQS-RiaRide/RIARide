package com.riaride.fleet.internal.persistence;

import com.riaride.fleet.internal.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

  // The code is the id the simulator uses in its MQTT topics.
  Optional<Vehicle> findByCode(String code);

  List<Vehicle> findByHubIdOrderByCode(UUID hubId);

  // Codes are unique regardless of case, so EB-401 and eb-401 cannot both exist (V6). Written
  // with lower() to match the V6 index; the derived query would use upper() and scan the table.
  @Query("select count(v) > 0 from Vehicle v where lower(v.code) = lower(:code)")
  boolean existsByCodeIgnoreCase(@Param("code") String code);

  // The vehicles docked at a hub, which take its slots (BR10). A ridden vehicle has no hub.
  long countByHubId(UUID hubId);
}
