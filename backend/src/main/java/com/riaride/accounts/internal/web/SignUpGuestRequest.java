package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.SignUpGuestCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The sign-up form. toString hides the password and the stay code (QA Manual 3.4).
record SignUpGuestRequest(
	@NotBlank @Email @Size(max = 254) String email,
	@NotBlank @Size(min = 8, max = 128) String password,
	@NotBlank @Size(max = 32) String stayCode,
	// The UI language chosen by the guest; English when absent.
	@Pattern(regexp = "^(pt|en)$") String locale) {

	static final String DEFAULT_LOCALE = "en";

	SignUpGuestCommand toCommand() {
		return new SignUpGuestCommand(email, password, stayCode, locale == null ? DEFAULT_LOCALE : locale);
	}

	@Override
	public String toString() {
		return "SignUpGuestRequest[email=" + email + ", password=********, stayCode=********, locale=" + locale + "]";
	}

}
