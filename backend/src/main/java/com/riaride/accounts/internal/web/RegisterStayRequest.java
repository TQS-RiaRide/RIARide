package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.RegisterStayCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

record RegisterStayRequest(
	@NotNull UUID hotelId,
	@NotBlank @Size(max = 120) String guestName,
	@NotNull LocalDate checkIn,
	@NotNull LocalDate checkOut) {

	RegisterStayCommand toCommand() {
		return new RegisterStayCommand(hotelId, guestName.strip(), checkIn, checkOut);
	}

}
