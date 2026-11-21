package com.riaride.accounts.internal.application;

import java.util.Objects;
import java.util.UUID;

// Who registers a stay: an admin may register at any hotel of the group,
// a hotel manager only at the hotel they manage.
public final class StayRegistrar {

	private final boolean admin;
	private final UUID managedHotelId;

	private StayRegistrar(boolean admin, UUID managedHotelId) {
		this.admin = admin;
		this.managedHotelId = managedHotelId;
	}

	public static StayRegistrar admin() {
		return new StayRegistrar(true, null);
	}

	// managedHotelId may be null when the manager's account has no hotel; they can then register nowhere.
	public static StayRegistrar managerOf(UUID managedHotelId) {
		return new StayRegistrar(false, managedHotelId);
	}

	boolean canRegisterAt(UUID hotelId) {
		return admin || Objects.equals(managedHotelId, hotelId);
	}

}
