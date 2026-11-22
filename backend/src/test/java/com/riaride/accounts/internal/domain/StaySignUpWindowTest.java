package com.riaride.accounts.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a stay code can be used to sign up until the end of the check-out day
// (BR1: only guests with an active stay can register).
class StaySignUpWindowTest {

	private static final UUID MARES_CENTRO = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final StayCode CODE = new StayCode("K7M3Q9TX");
	private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 1);
	private static final LocalDate CHECK_OUT = LocalDate.of(2026, 12, 5);

	private static Stay stay() {
		return Stay.register(MARES_CENTRO, "Rita Almeida", CODE, CHECK_IN, CHECK_OUT, CHECK_IN);
	}

	@Requirement("RIA-51")
	@Test
	void shouldNotHaveEndedOnTheDayBeforeCheckOut() {
		assertThat(stay().hasEndedOn(CHECK_OUT.minusDays(1))).isFalse();
	}

	@Requirement("RIA-51")
	@Test
	void shouldNotHaveEndedOnTheCheckOutDay() {
		assertThat(stay().hasEndedOn(CHECK_OUT)).isFalse();
	}

	@Requirement("RIA-51")
	@Test
	void shouldHaveEndedOnTheDayAfterCheckOut() {
		assertThat(stay().hasEndedOn(CHECK_OUT.plusDays(1))).isTrue();
	}

}
