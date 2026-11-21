package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.domain.Guest;
import java.util.UUID;

record GuestResponse(UUID id, String email, String locale) {

	static GuestResponse from(Guest guest) {
		return new GuestResponse(guest.getId(), guest.getEmail(), guest.getLocale());
	}

}
