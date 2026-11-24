package com.riaride.passes.internal.persistence;

import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassRepository extends JpaRepository<Pass, UUID> {

  boolean existsByGuestIdAndStatusIn(UUID guestId, Collection<PassStatus> statuses);

  long deleteByIdAndStatus(UUID id, PassStatus status);
}
