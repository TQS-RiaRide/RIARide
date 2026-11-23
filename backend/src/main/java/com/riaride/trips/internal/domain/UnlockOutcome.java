package com.riaride.trips.internal.domain;

// What an UNLOCKED reading does to the bike's latest trip (RIA-54).
public enum UnlockOutcome {
	// The trip was starting and the unlock came in time: it is now in use.
	STARTED,
	// The trip is already in use: the simulator keeps reporting UNLOCKED while the bike is ridden.
	ALREADY_STARTED,
	// The trip failed, or the unlock came at or after the timeout: the bike must be locked again.
	TOO_LATE,
	// The reading was emitted before the trip was requested (a redelivered QoS 1 message): ignored.
	STALE
}
