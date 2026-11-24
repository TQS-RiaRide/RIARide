package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.HubWithoutFreeSlotException;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleDocking;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.TripRepository;
import com.riaride.trips.internal.persistence.TripState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

// Unit test (Surefire): ending a guest's trip at a hub (RIA-55, BR10). The bike is docked first,
// then the trip is locked, checked again and ended, and LOCK is published for that hub. A full hub
// leaves the trip in use. Another guest's trip is not found, and a trip not in use is refused,
// without touching the bike.
@ExtendWith(MockitoExtension.class)
class EndTripServiceTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final String KEYCLOAK_ID = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
  private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000c0001");
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0301");
  private static final UUID START_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID END_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a002");

  @Mock private GuestDirectory guests;

  @Mock private TripRepository trips;

  @Mock private VehicleDocking docking;

  @Mock private ApplicationEventPublisher events;

  private EndTripService service;
  private Trip trip;

  @BeforeEach
  void setUp() {
    service = new EndTripService(guests, trips, docking, events, Clock.fixed(NOW, ZoneOffset.UTC));
    var requested = NOW.minus(Duration.ofMinutes(40));
    var reservation =
        Reservation.hold(
            PASS,
            new ReservedVehicle(VEHICLE, "EB-301", START_HUB),
            requested,
            Duration.ofMinutes(15));
    trip = Trip.requestStart(reservation, GUEST, requested);
    trip.confirmUnlock(requested, requested.plusSeconds(5), Duration.ofSeconds(30));
  }

  private static TripState state(TripStatus status) {
    return new TripState() {
      @Override
      public TripStatus getStatus() {
        return status;
      }

      @Override
      public UUID getVehicleId() {
        return VEHICLE;
      }
    };
  }

  private void givenTheGuestsTripIs(TripStatus status) {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(trips.findStateByIdAndGuestId(trip.getId(), GUEST)).thenReturn(Optional.of(state(status)));
  }

  private Optional<Trip> end() {
    return service.end(new EndTripCommand(KEYCLOAK_ID, trip.getId(), END_HUB));
  }

  @Requirement("RIA-55")
  @Test
  void shouldDockTheBikeEndTheTripAndLockTheBikeAtTheHub() {
    givenTheGuestsTripIs(TripStatus.IN_USE);
    when(trips.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

    var ended = end();

    assertThat(ended).containsSame(trip);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.ENDED);
    assertThat(trip.getEndedAt()).isEqualTo(NOW);
    assertThat(trip.getEndHubId()).isEqualTo(END_HUB);
    var order = inOrder(docking, trips);
    order.verify(docking).dock(VEHICLE, END_HUB);
    order.verify(trips).findByIdForUpdate(trip.getId());
    verify(events).publishEvent(new TripEnded(trip.getId(), "EB-301", END_HUB));
  }

  @Requirement("RIA-55")
  @Test
  void shouldNotFindAnotherGuestsTrip() {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(trips.findStateByIdAndGuestId(trip.getId(), GUEST)).thenReturn(Optional.empty());

    assertThat(end()).isEmpty();
    verifyNoInteractions(docking, events);
  }

  @Requirement("RIA-55")
  @Test
  void shouldNotFindATripForAUserWhoIsNotAGuest() {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.empty());

    assertThat(end()).isEmpty();
    verifyNoInteractions(trips, docking, events);
  }

  @Requirement("RIA-55")
  @Test
  void shouldRefuseATripThatIsNotInUseWithoutTouchingTheBike() {
    givenTheGuestsTripIs(TripStatus.STARTING);

    assertThatThrownBy(this::end).isInstanceOf(TripNotInProgressException.class);
    verifyNoInteractions(docking, events);
  }

  // BR10: the trip continues, and the refusal reaches the guest with the suggested hubs.
  @Requirement("RIA-55")
  @Test
  void shouldLeaveTheTripInUseWhenTheHubIsFull() {
    givenTheGuestsTripIs(TripStatus.IN_USE);
    var full = new HubWithoutFreeSlotException(List.of());
    doThrow(full).when(docking).dock(VEHICLE, END_HUB);

    assertThatThrownBy(this::end).isSameAs(full);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
    verify(trips, never()).findByIdForUpdate(any());
    verifyNoInteractions(events);
  }

  // Two ends of the same trip at once: the second finds it ended under the lock. Throwing rolls
  // back its docking, so the bike is docked once.
  @Requirement("RIA-55")
  @Test
  void shouldRefuseWhenAConcurrentEndAlreadyEndedTheTrip() {
    givenTheGuestsTripIs(TripStatus.IN_USE);
    trip.end(START_HUB, NOW.minusSeconds(1));
    when(trips.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

    assertThatThrownBy(this::end).isInstanceOf(TripNotInProgressException.class);
    assertThat(trip.getEndHubId()).isEqualTo(START_HUB);
    verifyNoInteractions(events);
  }
}
