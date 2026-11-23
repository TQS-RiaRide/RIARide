package com.riaride.accounts.internal.application;

// What the guest typed in the sign-up form. toString hides the password and the stay code,
// so neither reaches logs by accident.
public record SignUpGuestCommand(String email, String password, String stayCode, String locale) {

	@Override
	public String toString() {
		return "SignUpGuestCommand[email=" + email + ", password=********, stayCode=********, locale=" + locale + "]";
	}

}
