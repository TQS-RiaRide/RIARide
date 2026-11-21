package com.riaride.passes.internal.domain;

import java.util.EnumSet;
import java.util.Set;

// INACTIVE until the first trip (BR4), ACTIVE while it can be used, CLOSED once it has ended.
public enum PassStatus {
	INACTIVE,
	ACTIVE,
	CLOSED;

	// A guest may hold only one open pass at a time (Product Owner decision, RIA-52).
	public static final Set<PassStatus> OPEN = EnumSet.of(INACTIVE, ACTIVE);

	public boolean isOpen() {
		return OPEN.contains(this);
	}
}
