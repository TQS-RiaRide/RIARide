package com.riaride.passes.internal.application;

import com.riaride.passes.PassUsage;
import com.riaride.passes.UsablePass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.persistence.PassRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Finds the pass a guest can ride with, and activates it on the first trip (RIA-54). A guest holds
// at most one open pass (uq_pass_open_per_guest), so the open one is the only candidate.
@Service
class PassUsageService implements PassUsage {

  private final PassRepository passes;
  private final Clock clock;

  PassUsageService(PassRepository passes, Clock clock) {
    this.passes = passes;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UsablePass> findUsablePass(UUID guestId) {
    var now = clock.instant();
    return passes
        .findFirstByGuestIdAndStatusIn(guestId, PassStatus.OPEN)
        .filter(pass -> pass.isUsableAt(now))
        .map(pass -> new UsablePass(pass.getId()));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean canStartTrip(UUID passId, Instant at) {
    return passes.findByIdForUpdate(passId).filter(pass -> pass.isUsableAt(at)).isPresent();
  }

  // MANDATORY: the pass is activated in the same transaction that starts the trip, or not at all.
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void activateOnFirstTrip(UUID passId, Instant at) {
    passes
        .findById(passId)
        .orElseThrow(() -> new IllegalStateException("The pass of a trip no longer exists"))
        .activate(at);
  }
}
