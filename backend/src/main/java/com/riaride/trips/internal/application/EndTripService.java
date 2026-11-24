package com.riaride.trips.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.fleet.VehicleDocking;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Ends a guest's trip at a hub with a free slot (RIA-55, BR10): the bike is docked and available,
// the trip is ENDED, and LOCK is sent at that hub once this transaction commits. A full hub refuses
// the end, with the nearest hubs that have a free slot, and the trip continues.
//
// The guest may end their trip even after their stay ended: a bike still out at check-out has to
// be returned (BR9). Locks are taken in the same order as telemetry: the vehicle row
// (fleet), then the trip row, so a reading of the same bike never deadlocks with the end. The trip
// is first read as a projection, which does not load it, so the locked read returns its current
// state; under the lock it is checked again, since a concurrent end may have won.
@Service
public class EndTripService {

  private final GuestDirectory guests;
  private final TripRepository trips;
  private final VehicleDocking docking;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public EndTripService(
      GuestDirectory guests,
      TripRepository trips,
      VehicleDocking docking,
      ApplicationEventPublisher events,
      Clock clock) {
    this.guests = guests;
    this.trips = trips;
    this.docking = docking;
    this.events = events;
    this.clock = clock;
  }

  // Empty when the trip does not exist or belongs to someone else.
  @Transactional
  public Optional<Trip> end(EndTripCommand command) {
    var trip =
        guests
            .findGuestId(command.keycloakId())
            .flatMap(guestId -> trips.findStateByIdAndGuestId(command.tripId(), guestId));
    if (trip.isEmpty()) {
      return Optional.empty();
    }
    if (trip.get().getStatus() != TripStatus.IN_USE) {
      throw new TripNotInProgressException();
    }
    docking.dock(trip.get().getVehicleId(), command.hubId());
    var locked = trips.findByIdForUpdate(command.tripId()).orElseThrow();
    if (locked.getStatus() != TripStatus.IN_USE) {
      throw new TripNotInProgressException();
    }
    locked.end(command.hubId(), clock.instant());
    events.publishEvent(new TripEnded(locked.getId(), locked.getVehicleCode(), command.hubId()));
    return Optional.of(locked);
  }
}
