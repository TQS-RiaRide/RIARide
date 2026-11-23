package com.riaride.accounts.internal.domain;

import java.util.regex.Pattern;

// The code a hotel hands to a guest at check-in, used to sign up.
// Eight characters from an alphabet without 0, O, 1 and I, which are easy to confuse.
// toString hides the value, so the code never reaches logs by accident.
public record StayCode(String value) {

	public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	public static final int LENGTH = 8;

	private static final Pattern FORMAT = Pattern.compile("^[" + ALPHABET + "]{" + LENGTH + "}$");

	public StayCode {
		if (value == null || !FORMAT.matcher(value).matches()) {
			throw new IllegalArgumentException("Stay code does not have the expected format");
		}
	}

	@Override
	public String toString() {
		return "StayCode[********]";
	}

}
