package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria;
import org.springframework.modulith.events.core.TargetEventPublication;

// Unit test (Surefire): an overtime charge that failed for now is resubmitted with exponential
// backoff, up to a maximum number of attempts (RIA-88, architecture section 7). Only overtime
// charges of ended trips are resubmitted. After the last attempt the charge is recorded as unpaid,
// for the deposit fallback (RIA-114), and its publication leaves the registry, so exhausted
// publications never crowd out newer ones (Spring Modulith reads failed publications oldest first).
// A publication starts at 1 attempt; each resubmission adds one.
@ExtendWith(MockitoExtension.class)
class OvertimeRetryJobTest {

  private static final Instant NOW = Instant.parse("2026-11-30T14:00:00Z");
  private static final Duration FIRST_DELAY = Duration.ofMinutes(1);
  private static final int MAX_ATTEMPTS = 5;

  @Mock private IncompleteEventPublications publications;

  @Mock private EventPublicationRepository registry;

  @Mock private OvertimeChargeListener listener;

  private OvertimeRetryJob job;
  private Predicate<EventPublication> resubmitted;

  @BeforeEach
  void runTheJob() {
    job =
        new OvertimeRetryJob(
            publications,
            registry,
            listener,
            Clock.fixed(NOW, ZoneOffset.UTC),
            FIRST_DELAY,
            MAX_ATTEMPTS);
    job.retryFailedCharges();
    var options = ArgumentCaptor.forClass(ResubmissionOptions.class);
    verify(publications).resubmitIncompletePublications(options.capture());
    resubmitted = options.getValue().getFilter();
  }

  private static TargetEventPublication failed(Object event, int attempts) {
    var publication =
        mock(TargetEventPublication.class, withSettings().strictness(Strictness.LENIENT));
    when(publication.getIdentifier()).thenReturn(UUID.randomUUID());
    when(publication.getEvent()).thenReturn(event);
    when(publication.getCompletionAttempts()).thenReturn(attempts);
    return publication;
  }

  // Lenient: the filter may decide before it reads every property.
  private static EventPublication lenientPublication() {
    return mock(EventPublication.class, withSettings().strictness(Strictness.LENIENT));
  }

  private static EventPublication publication(Object event, int attempts, Duration sinceLast) {
    var publication = lenientPublication();
    when(publication.getEvent()).thenReturn(event);
    when(publication.getCompletionAttempts()).thenReturn(attempts);
    when(publication.getLastPublicationDate()).thenReturn(NOW.minus(sinceLast));
    return publication;
  }

  private static TripEnded tripEnded() {
    return new TripEnded(UUID.randomUUID(), "EB-301", UUID.randomUUID());
  }

  @Requirement("RIA-88")
  @Test
  void shouldRetryAChargeOnceTheFirstDelayHasPassed() {
    assertThat(resubmitted).accepts(publication(tripEnded(), 1, FIRST_DELAY));
    assertThat(resubmitted).rejects(publication(tripEnded(), 1, FIRST_DELAY.minusSeconds(1)));
  }

  // Each attempt doubles the wait: after the 3rd attempt, 4 minutes.
  @Requirement("RIA-88")
  @Test
  void shouldDoubleTheDelayAfterEachAttempt() {
    assertThat(resubmitted)
        .rejects(publication(tripEnded(), 3, Duration.ofMinutes(4).minusSeconds(1)));
    assertThat(resubmitted).accepts(publication(tripEnded(), 3, Duration.ofMinutes(4)));
  }

  @Requirement("RIA-88")
  @Test
  void shouldStopAfterTheLastAttempt() {
    assertThat(resubmitted).rejects(publication(tripEnded(), MAX_ATTEMPTS, Duration.ofDays(1)));
  }

  // Other modules' publications are theirs to handle.
  @Requirement("RIA-88")
  @Test
  void shouldOnlyRetryOvertimeChargesOfEndedTrips() {
    var other = lenientPublication();
    when(other.getEvent()).thenReturn("another event");

    assertThat(resubmitted).rejects(other);
  }

  // The last attempt failed too: the charge is recorded as unpaid and the publication removed.
  @Requirement("RIA-88")
  @Test
  void shouldGiveUpAChargeWhoseLastAttemptFailed() {
    var ended = tripEnded();
    var exhausted = failed(ended, MAX_ATTEMPTS);
    when(registry.findFailedPublications(any(FailedCriteria.class))).thenReturn(List.of(exhausted));

    job.retryFailedCharges();

    verify(listener).giveUp(ended);
    verify(registry).deletePublications(List.of(exhausted.getIdentifier()));
  }

  @Requirement("RIA-88")
  @Test
  void shouldNotGiveUpAChargeWithAttemptsLeft() {
    var withAttemptsLeft = failed(tripEnded(), MAX_ATTEMPTS - 1);
    when(registry.findFailedPublications(any(FailedCriteria.class)))
        .thenReturn(List.of(withAttemptsLeft));

    job.retryFailedCharges();

    verify(listener, never()).giveUp(any());
    verify(registry, never()).deletePublications(any());
  }

  // Other modules' failed publications are theirs to handle.
  @Requirement("RIA-88")
  @Test
  void shouldLeaveOtherExhaustedPublicationsAlone() {
    var other = failed("another event", MAX_ATTEMPTS);
    when(registry.findFailedPublications(any(FailedCriteria.class))).thenReturn(List.of(other));

    job.retryFailedCharges();

    verify(listener, never()).giveUp(any());
    verify(registry, never()).deletePublications(any());
  }
}
