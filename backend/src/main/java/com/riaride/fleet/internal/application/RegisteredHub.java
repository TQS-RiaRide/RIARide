package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Hub;

// A hub just registered, with the name of its hotel (null for a tourist spot hub).
public record RegisteredHub(Hub hub, String hotelName) {
}
