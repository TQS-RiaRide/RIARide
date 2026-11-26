package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

// Unit test (Surefire): an overtime charge that failed for now is resubmitted with exponential
// backoff, up to a maximum number of attempts (RIA-88, architecture section 7). Only overtime
// charges of ended trips are resubmitted. After the last attempt the charge is left to the deposit
// (RIA-114).
@ExtendWith(MockitoExtension.class)
class OvertimeRetryJobTest {

  private static final Instant NOW = Instant.parse("2026-11-30T14:00:00Z");
  private static final Duration FIRST_DELAY = Duration.ofMinutes(1);
  private static final int MAX_ATTEMPTS = 5;

  @Mock private IncompleteEventPublications publications;

  private Predicate<EventPublication> resubmitted;

  @BeforeEach
  void runTheJob() {
    var job =
        new OvertimeRetryJob(
            publications, Clock.fixed(NOW, ZoneOffset.UTC), FIRST_DELAY, MAX_ATTEMPTS);
    job.retryFailedCharges();
    var options = ArgumentCaptor.forClass(ResubmissionOptions.class);
    verify(publications).resubmitIncompletePublications(options.capture());
    resubmitted = options.getValue().getFilter();
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
}
