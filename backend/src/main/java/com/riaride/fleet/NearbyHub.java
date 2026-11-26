package com.riaride.fleet;

import java.util.UUID;

// A hub with a free slot suggested instead of a full one (RIA-55), with its straight-line distance
// from the full hub, rounded to the metre.
public record NearbyHub(UUID hubId, String name, int freeSlots, long distanceMeters) {}
