package com.riaride.fleet.internal.domain;

// Text rules shared by the names the fleet module accepts: hub names and the hotel names used to
// find a hub's hotel.
public final class Names {

	private Names() {
	}

	// Removes the spaces at both ends of a name. A space is what String.strip removes plus every
	// Unicode separator (category Z), such as the no-break spaces U+00A0, U+2007 and U+202F, which
	// String.strip keeps. Returns an empty string when the name is only spaces.
	public static String stripSpaces(String name) {
		int start = 0;
		int end = name.length();
		while (start < end && isSpace(name.codePointAt(start))) {
			start += Character.charCount(name.codePointAt(start));
		}
		while (end > start && isSpace(name.codePointBefore(end))) {
			end -= Character.charCount(name.codePointBefore(end));
		}
		return name.substring(start, end);
	}

	private static boolean isSpace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
	}

}
