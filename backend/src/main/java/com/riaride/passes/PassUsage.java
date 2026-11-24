package com.riaride.passes;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Public API of the passes module for the trips module (RIA-54).
public interface PassUsage {

  // The guest's pass, if it can be used for a trip now: paid and not active yet (BR4), or active
  // and not past its end.
  Optional<UsablePass> findUsablePass(UUID guestId);

  // BR4: the first confirmed trip activates the pass; later trips keep the first activation time.
  // Joins the caller's transaction.
  void activateOnFirstTrip(UUID passId, Instant at);
}
