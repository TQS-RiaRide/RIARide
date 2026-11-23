package com.riaride.fleet.internal.persistence;

import com.riaride.fleet.internal.domain.Hub;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HubRepository extends JpaRepository<Hub, UUID> {

	// Exact match: names that differ only in letter case are different hubs.
	boolean existsByName(String name);

	// Locks the hub row until the transaction ends (SELECT ... FOR UPDATE), so two requests that
	// dock a bike at the same hub check its free slots one after the other (BR10).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from Hub h where h.id = :id")
	Optional<Hub> findByIdForUpdate(@Param("id") UUID id);

}
