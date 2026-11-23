package com.riaride.trips.internal.application;

import java.util.UUID;

// A start failed (BR13), or the bike unlocked after its latest trip failed: once the transaction
// commits, the bike is sent LOCK at the hub it was reserved at (null: where it is).
record TripFailed(String vehicleCode, UUID hubId) {
}
