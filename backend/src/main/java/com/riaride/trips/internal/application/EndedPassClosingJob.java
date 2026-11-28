package com.riaride.trips.internal.application;

import com.riaride.passes.PassUsage;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

// Closes the passes that ended (expiry or check-out, BR8, BR9) with no trip in progress (RIA-89). A
// guest holds only one open pass at a time (RIA-52), so an ended pass left open would stop them
// buying another. A pass whose trip is still starting or in use is left alone: the trip continues,
// and its end closes the pass (EndTripService). A paid pass that was never used is closed once the
// stay has ended. It lives in trips, since passes cannot depend on trips. Each pass is closed in a
// transaction of its own; one that fails is tried again on the next run.
//
// A start that read the pass just before it ended can still commit a starting trip after the check.
// UnlockConfirmationListener then checks the pass again under its lock, finds it closed, fails the
// trip and locks the bike again (BR13), so no trip runs on a closed pass.
@Component
public class EndedPassClosingJob {

  private static final Logger log = LoggerFactory.getLogger(EndedPassClosingJob.class);

  private final PassUsage passes;
  private final TripRepository trips;
  private final Clock clock;
  private final TransactionOperations transactions;

  EndedPassClosingJob(
      PassUsage passes, TripRepository trips, Clock clock, TransactionOperations transactions) {
    this.passes = passes;
    this.trips = trips;
    this.clock = clock;
    this.transactions = transactions;
  }

  // Returns how many passes it closed.
  @Scheduled(fixedDelayString = "${riaride.trips.ended-pass-check-interval}")
  public int closeEndedPasses() {
    var now = clock.instant();
    var closed = 0;
    for (var passId : passes.findEndedPasses(now)) {
      try {
        if (Boolean.TRUE.equals(transactions.execute(status -> close(passId, now)))) {
          closed++;
        }
      } catch (RuntimeException notClosed) {
        log.error(
            "Ended pass {} not closed ({}); retrying on the next run",
            passId,
            notClosed.getClass().getSimpleName());
      }
    }
    return closed;
  }

  private boolean close(UUID passId, Instant now) {
    if (trips.existsByPassIdAndStatusIn(passId, TripStatus.ACTIVE)) {
      return false;
    }
    return passes.closeIfEnded(passId, now);
  }
}
