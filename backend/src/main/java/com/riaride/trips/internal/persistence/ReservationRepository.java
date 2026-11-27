package com.riaride.trips.internal.persistence;

import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

  // A pass has one active reservation at most (uq_reservation_active_per_pass). Locked, so a start
  // that reuses or ends the hold never races ReservationExpiryJob.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Reservation> findByPassIdAndStatus(UUID passId, ReservationStatus status);

  // Ids only: ReservationExpiryJob then expires each reservation in a transaction of its own.
  @Query("select r.id from Reservation r where r.status = :status and r.expiresAt <= :now")
  List<UUID> findIdsByStatusAndExpiresAtLessThanEqual(
      @Param("status") ReservationStatus status, @Param("now") Instant now);

  // A bike has at most one active reservation (V7, uq_reservation_active_per_vehicle).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Reservation> findByVehicleIdAndStatus(UUID vehicleId, ReservationStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id = :id")
  Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);
}
