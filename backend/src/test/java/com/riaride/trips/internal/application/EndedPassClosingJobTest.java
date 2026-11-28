package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.passes.PassUsage;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

// Unit test (Surefire): the job that closes passes that ended with no trip in progress (RIA-89,
// BR8, BR9). An ended pass that stays open would stop the guest from buying another one (one open
// pass per guest, RIA-52). A pass whose trip is still starting or in use is left alone: the end of
// that trip closes it (EndTripService). Each pass is closed in a transaction of its own, so a
// failure does not stop the others.
@ExtendWith(MockitoExtension.class)
class EndedPassClosingJobTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID OTHER_PASS = UUID.fromString("1db8bee0-1e3a-491f-91c6-09cfe94a8cfb");

  @Mock private PassUsage passes;

  @Mock private TripRepository trips;

  private EndedPassClosingJob job;

  @BeforeEach
  void setUp() {
    job =
        new EndedPassClosingJob(
            passes,
            trips,
            Clock.fixed(NOW, ZoneOffset.UTC),
            TransactionOperations.withoutTransaction());
  }

  @Requirement("RIA-89")
  @Test
  void shouldCloseAnEndedPassWithNoTripInProgress() {
    when(passes.findEndedPasses(NOW)).thenReturn(List.of(PASS));
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(false);
    when(passes.closeIfEnded(PASS, NOW)).thenReturn(true);

    assertThat(job.closeEndedPasses()).isEqualTo(1);
    verify(passes).closeIfEnded(PASS, NOW);
  }

  // BR8: the trip continues after the pass ends, so the pass stays open until the trip ends.
  @Requirement("RIA-89")
  @Test
  void shouldLeaveAnEndedPassWhoseTripIsStillInProgress() {
    when(passes.findEndedPasses(NOW)).thenReturn(List.of(PASS));
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(true);

    assertThat(job.closeEndedPasses()).isZero();
    verify(passes, never()).closeIfEnded(any(), any());
  }

  // Closed by the end of its trip between the search and the close: nothing is counted.
  @Requirement("RIA-89")
  @Test
  void shouldNotCountAPassThatWasAlreadyClosed() {
    when(passes.findEndedPasses(NOW)).thenReturn(List.of(PASS));
    when(trips.existsByPassIdAndStatusIn(PASS, TripStatus.ACTIVE)).thenReturn(false);
    when(passes.closeIfEnded(PASS, NOW)).thenReturn(false);

    assertThat(job.closeEndedPasses()).isZero();
  }

  @Requirement("RIA-89")
  @Test
  void shouldKeepClosingTheOtherPassesWhenOneFails() {
    when(passes.findEndedPasses(NOW)).thenReturn(List.of(PASS, OTHER_PASS));
    when(trips.existsByPassIdAndStatusIn(any(), any())).thenReturn(false);
    when(passes.closeIfEnded(PASS, NOW)).thenThrow(new IllegalStateException("lock timeout"));
    when(passes.closeIfEnded(OTHER_PASS, NOW)).thenReturn(true);

    assertThat(job.closeEndedPasses()).isEqualTo(1);
    verify(passes).closeIfEnded(OTHER_PASS, NOW);
  }

  @Requirement("RIA-89")
  @Test
  void shouldDoNothingWhenNoPassHasEnded() {
    when(passes.findEndedPasses(NOW)).thenReturn(List.of());

    assertThat(job.closeEndedPasses()).isZero();
    verify(passes, never()).closeIfEnded(any(), any());
  }
}
