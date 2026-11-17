package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.RegisterStayCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

record RegisterStayRequest(
	@NotNull UUID hotelId,
	// No control characters: PostgreSQL rejects NUL, and none belongs in a name.
	@NotBlank @Size(max = 120) @Pattern(regexp = NO_CONTROL_CHARACTERS) String guestName,
	@NotNull LocalDate checkIn,
	@NotNull LocalDate checkOut) {

	static final String NO_CONTROL_CHARACTERS = "^\\P{Cntrl}*$";

	RegisterStayCommand toCommand() {
		return new RegisterStayCommand(hotelId, guestName.strip(), checkIn, checkOut);
	}

}
