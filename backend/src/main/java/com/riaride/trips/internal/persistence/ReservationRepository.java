package com.riaride.trips.internal.persistence;

import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

  // A pass has one active reservation at most (uq_reservation_active_per_pass).
  Optional<Reservation> findByPassIdAndStatus(UUID passId, ReservationStatus status);
}
