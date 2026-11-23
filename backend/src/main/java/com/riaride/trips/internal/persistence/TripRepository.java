package com.riaride.trips.internal.persistence;

import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

	boolean existsByPassIdAndStatusIn(UUID passId, Collection<TripStatus> statuses);

	// The bike's latest trip, which an UNLOCKED reading confirms or answers with LOCK. Locked
	// (SELECT ... FOR UPDATE), so the unlock confirmation and the timeout job never decide the same
	// trip at once. The vehicle code is the id telemetry reports.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Trip> findFirstByVehicleCodeOrderByRequestedAtDesc(String vehicleCode);

	// Ids only: the timeout job then fails each trip in a transaction of its own.
	@Query("select t.id from Trip t where t.status = :status and t.requestedAt <= :cutoff")
	List<UUID> findIdsByStatusAndRequestedAtLessThanEqual(@Param("status") TripStatus status,
		@Param("cutoff") Instant cutoff);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Trip t where t.id = :id")
	Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

	Optional<Trip> findByIdAndGuestId(UUID id, UUID guestId);

}
