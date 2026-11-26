package com.riaride.passes;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Public API of the passes module for the trips module (RIA-54).
public interface PassUsage {

  // The guest's pass, if it can be used for a trip now: paid and not active yet (BR4), or active
  // and not past its end.
  Optional<UsablePass> findUsablePass(UUID guestId);

  // Whether the pass can still take a trip that starts at the given time: telemetry confirmed the
  // unlock, but the pass may have changed meanwhile. Locks the pass until the caller's transaction
  // ends, so it cannot change before activateOnFirstTrip. Joins the caller's transaction.
  boolean canStartTrip(UUID passId, Instant at);

  // BR4: the first confirmed trip activates the pass; later trips keep the first activation time.
  // Joins the caller's transaction.
  void activateOnFirstTrip(UUID passId, Instant at);

  // The limits of an ended trip's overtime (RIA-88): when the pass expires and the stay ends.
  PassLimits limitsOf(UUID passId);
}
