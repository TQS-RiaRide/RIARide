package com.riaride.trips.internal.persistence;

import com.riaride.trips.internal.domain.TripStatus;
import java.util.UUID;

// What ending a trip needs before it locks the trip: its status and its bike (RIA-55). A
// projection, so the trip itself is not loaded into the persistence context.
public interface TripState {

  TripStatus getStatus();

  UUID getVehicleId();
}
