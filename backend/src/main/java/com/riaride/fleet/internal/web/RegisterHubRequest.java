package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.RegisterHubCommand;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The hub registration form. The rules on name, kind, hotel, position and capacity are the
// domain's (Hub.register); here only the numbers' presence and the hotel name's shape are checked,
// since the domain takes plain numbers and the hotel name is looked up in the database.
record RegisterHubRequest(
	String name,
	HubKind kind,
	// No control characters: PostgreSQL rejects NUL, and none belongs in a name.
	@Size(max = Hub.MAX_NAME_LENGTH, message = "No hotel of the group has a name this long.")
	@Pattern(regexp = NO_CONTROL_CHARACTERS, message = "A hotel name must not contain control characters.")
	String hotel,
	@NotNull(message = "The latitude is required.") Double latitude,
	@NotNull(message = "The longitude is required.") Double longitude,
	@NotNull(message = "The capacity is required.") Integer capacity) {

	static final String NO_CONTROL_CHARACTERS = "^\\P{Cntrl}*$";

	RegisterHubCommand toCommand() {
		return new RegisterHubCommand(name, kind, hotel == null ? null : hotel.strip(), latitude, longitude, capacity);
	}

}
