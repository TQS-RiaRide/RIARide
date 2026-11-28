package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.maintenance.MaintenanceTicketOpened;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
import com.riaride.trips.internal.persistence.ReservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): a ticket opened for a reserved bike cancels the bike's active reservation
// (RIA-92), so the guest's pass can hold another bike. A bike without an active reservation
// changes nothing. CancelReservationOnTicketIT runs it after the ticket's transaction commits.
@ExtendWith(MockitoExtension.class)
class ReservationCancellationListenerTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0911");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");

  @Mock private ReservationRepository reservations;

  private ReservationCancellationListener listener;

  @BeforeEach
  void setUp() {
    listener = new ReservationCancellationListener(reservations);
  }

  private static MaintenanceTicketOpened ticketFor(UUID vehicleId) {
    return new MaintenanceTicketOpened(UUID.randomUUID(), vehicleId, NOW);
  }

  @Requirement("RIA-92")
  @Test
  void shouldCancelTheActiveReservationOfTheBike() {
    var reservation =
        Reservation.hold(
            PASS,
            new ReservedVehicle(VEHICLE, "EB-911", HUB),
            NOW.minusSeconds(60),
            Duration.ofMinutes(15));
    when(reservations.findByVehicleIdAndStatus(VEHICLE, ReservationStatus.ACTIVE))
        .thenReturn(Optional.of(reservation));

    listener.on(ticketFor(VEHICLE));

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
  }

  @Requirement("RIA-92")
  @Test
  void shouldDoNothingWhenTheBikeHasNoActiveReservation() {
    when(reservations.findByVehicleIdAndStatus(VEHICLE, ReservationStatus.ACTIVE))
        .thenReturn(Optional.empty());

    listener.on(ticketFor(VEHICLE));
  }
}
