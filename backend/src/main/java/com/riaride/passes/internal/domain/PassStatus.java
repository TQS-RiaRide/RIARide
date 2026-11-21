package com.riaride.passes.internal.domain;

import java.util.EnumSet;
import java.util.Set;

// PENDING while its payment is in progress, INACTIVE once paid and until the first trip (BR4),
// ACTIVE while it can be used, CLOSED once it has ended.
public enum PassStatus {
	PENDING,
	INACTIVE,
	ACTIVE,
	CLOSED;

	// A guest may hold only one open pass at a time (Product Owner decision, RIA-52).
	// A pending pass counts too, so a concurrent purchase is refused before its card is charged.
	public static final Set<PassStatus> OPEN = EnumSet.of(PENDING, INACTIVE, ACTIVE);

	public boolean isOpen() {
		return OPEN.contains(this);
	}
}
