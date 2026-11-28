package com.riaride.passes.internal.persistence;

import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
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

public interface PassRepository extends JpaRepository<Pass, UUID> {

  boolean existsByGuestIdAndStatusIn(UUID guestId, Collection<PassStatus> statuses);

  long deleteByIdAndStatus(UUID id, PassStatus status);

  Optional<Pass> findFirstByGuestIdAndStatusIn(UUID guestId, Collection<PassStatus> statuses);

  // The passes that have ended by the given time (RIA-89): active ones past their expiry, and paid
  // ones never used whose stay has ended. Ids only: EndedPassClosingJob then closes each pass in a
  // transaction of its own.
  @Query(
      """
      select p.id from Pass p
      where p.status = com.riaride.passes.internal.domain.PassStatus.ACTIVE and p.expiresAt <= :at
         or p.status = com.riaride.passes.internal.domain.PassStatus.INACTIVE
            and p.stayEndsAt <= :at
      """)
  List<UUID> findIdsOfEndedPasses(@Param("at") Instant at);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Pass p where p.id = :id")
  Optional<Pass> findByIdForUpdate(@Param("id") UUID id);
}
