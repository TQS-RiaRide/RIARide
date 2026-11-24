package com.riaride.fleet.internal.persistence;

import com.riaride.fleet.internal.domain.Vehicle;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

  // The code is the id the simulator uses in its MQTT topics.
  Optional<Vehicle> findByCode(String code);

  List<Vehicle> findByHubIdOrderByCode(UUID hubId);

  boolean existsByCode(String code);

  // The vehicles docked at a hub, which take its slots (BR10). A ridden vehicle has no hub.
  long countByHubId(UUID hubId);

  // Locks the vehicle row until the transaction ends (SELECT ... FOR UPDATE), so two trips that
  // ask for the same bike reserve it one after the other, and only the first gets it (RIA-54).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Vehicle v where v.code = :code")
  Optional<Vehicle> findByCodeForUpdate(@Param("code") String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Vehicle v where v.id = :id")
  Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);
}
