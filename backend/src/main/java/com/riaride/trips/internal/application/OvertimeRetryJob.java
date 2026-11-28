package com.riaride.trips.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Spring Modulith keeps a failed publication but does not retry it on its own (architecture,
// section 7). This job resubmits overtime charges that failed for now, waiting twice as long after
// each attempt, up to a maximum. A charge whose last attempt failed too is recorded as unpaid, for
// the deposit fallback (RIA-114), and its publication is deleted: Spring Modulith reads failed
// publications oldest first, so exhausted ones left behind would crowd out newer charges. Only
// TripEnded publications are touched; only @ApplicationModuleListener listeners are in the
// registry (spring.modulith.events.registry-trigger-annotation), so the bike commands are never
// sent again. A publication starts at 1 attempt; each resubmission adds one.
@Component
public class OvertimeRetryJob {

  private static final Logger log = LoggerFactory.getLogger(OvertimeRetryJob.class);

  private final IncompleteEventPublications publications;
  private final EventPublicationRepository registry;
  private final OvertimeChargeListener listener;
  private final Clock clock;
  private final Duration firstDelay;
  private final int maxAttempts;

  OvertimeRetryJob(
      IncompleteEventPublications publications,
      EventPublicationRepository registry,
      OvertimeChargeListener listener,
      Clock clock,
      Duration firstDelay,
      int maxAttempts) {
    this.publications = publications;
    this.registry = registry;
    this.listener = listener;
    this.clock = clock;
    this.firstDelay = firstDelay;
    this.maxAttempts = maxAttempts;
  }

  @Autowired
  OvertimeRetryJob(
      IncompleteEventPublications publications,
      EventPublicationRepository registry,
      OvertimeChargeListener listener,
      Clock clock,
      OvertimeProperties properties) {
    this(
        publications,
        registry,
        listener,
        clock,
        properties.retryFirstDelay(),
        properties.retryMaxAttempts());
  }

  @Scheduled(fixedDelayString = "${riaride.trips.overtime.retry-check-interval}")
  public void retryFailedCharges() {
    giveUpExhaustedCharges();
    var now = clock.instant();
    publications.resubmitIncompletePublications(
        ResubmissionOptions.defaults().withFilter(publication -> isDue(publication, now)));
  }

  private void giveUpExhaustedCharges() {
    var exhausted =
        registry.findFailedPublications(FailedCriteria.ALL).stream()
            .filter(publication -> publication.getEvent() instanceof TripEnded)
            .filter(publication -> publication.getCompletionAttempts() >= maxAttempts)
            .toList();
    if (exhausted.isEmpty()) {
      return;
    }
    exhausted.forEach(publication -> listener.giveUp((TripEnded) publication.getEvent()));
    registry.deletePublications(
        exhausted.stream().map(TargetEventPublication::getIdentifier).toList());
    log.warn("Gave up {} overtime charge(s) after {} attempts", exhausted.size(), maxAttempts);
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
