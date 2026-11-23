package com.riaride.trips.internal.persistence;

import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {

	boolean existsByPassIdAndStatusIn(UUID passId, Collection<TripStatus> statuses);

	// The vehicle code is the id telemetry reports.
	Optional<Trip> findFirstByVehicleCodeAndStatus(String vehicleCode, TripStatus status);

	List<Trip> findByStatusAndRequestedAtLessThanEqual(TripStatus status, Instant cutoff);

}
