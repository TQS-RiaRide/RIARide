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
	// No Unicode control characters (PostgreSQL rejects NUL), line or paragraph separators, or
	// bidi embeddings, overrides and isolates, which could spoof how the name is shown. Other
	// format characters stay allowed: ZWNJ and ZWJ are part of how some names are spelled.
	@NotBlank @Size(max = 120) @Pattern(regexp = NO_CONTROL_CHARACTERS) String guestName,
	@NotNull LocalDate checkIn,
	@NotNull LocalDate checkOut) {

	static final String NO_CONTROL_CHARACTERS = "^[^\\p{Cc}\\p{Zl}\\p{Zp}\\x{202A}-\\x{202E}\\x{2066}-\\x{2069}]*$";

	RegisterStayCommand toCommand() {
		return new RegisterStayCommand(hotelId, guestName.strip(), checkIn, checkOut);
	}

}
