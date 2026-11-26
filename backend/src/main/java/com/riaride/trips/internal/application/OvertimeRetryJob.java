package com.riaride.trips.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Spring Modulith keeps a failed publication but does not retry it on its own (architecture,
// section 7). This job resubmits overtime charges that failed for now, waiting twice as long after
// each attempt, up to a maximum. After the last attempt the charge is left to the deposit
// (RIA-114). Only TripEnded publications are resubmitted; only @ApplicationModuleListener
// listeners are in the registry (spring.modulith.events.registry-trigger-annotation), so the bike
// commands are never sent again.
@Component
public class OvertimeRetryJob {

  private final IncompleteEventPublications publications;
  private final Clock clock;
  private final Duration firstDelay;
  private final int maxAttempts;

  OvertimeRetryJob(
      IncompleteEventPublications publications, Clock clock, Duration firstDelay, int maxAttempts) {
    this.publications = publications;
    this.clock = clock;
    this.firstDelay = firstDelay;
    this.maxAttempts = maxAttempts;
  }

  @Autowired
  OvertimeRetryJob(
      IncompleteEventPublications publications, Clock clock, OvertimeProperties properties) {
    this(publications, clock, properties.retryFirstDelay(), properties.retryMaxAttempts());
  }

  @Scheduled(fixedDelayString = "${riaride.trips.overtime.retry-check-interval}")
  public void retryFailedCharges() {
    var now = clock.instant();
    publications.resubmitIncompletePublications(
        ResubmissionOptions.defaults().withFilter(publication -> isDue(publication, now)));
  }

  private boolean isDue(EventPublication publication, Instant now) {
    if (!(publication.getEvent() instanceof TripEnded)) {
      return false;
    }
    var attempts = publication.getCompletionAttempts();
    if (attempts >= maxAttempts) {
      return false;
    }
    var delay = firstDelay.multipliedBy(1L << Math.max(0, attempts - 1));
    return !publication.getLastPublicationDate().plus(delay).isAfter(now);
  }
}
