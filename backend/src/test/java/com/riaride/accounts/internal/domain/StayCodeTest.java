package com.riaride.accounts.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): a stay code only accepts the expected format.
class StayCodeTest {

	@Requirement("RIA-50")
	@ParameterizedTest
	@ValueSource(strings = {"K7M3Q9TX", "ABCDEFGH", "23456789"})
	void shouldAcceptCodeWithExpectedFormat(String value) {
		assertThat(new StayCode(value).value()).isEqualTo(value);
	}

	@Requirement("RIA-50")
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "K7M3Q9T", "K7M3Q9TXA", "k7m3q9tx", "K7M3Q9T0", "K7M3Q9TO", "K7M3Q9T1", "K7M3Q9TI", "K7M3-9TX"})
	void shouldRejectCodeWithUnexpectedFormat(String value) {
		assertThatThrownBy(() -> new StayCode(value)).isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-50")
	@ParameterizedTest
	@ValueSource(strings = {"K7M3Q9TX"})
	void shouldNotRevealCodeInToString(String value) {
		assertThat(new StayCode(value).toString()).doesNotContain(value);
	}

}
