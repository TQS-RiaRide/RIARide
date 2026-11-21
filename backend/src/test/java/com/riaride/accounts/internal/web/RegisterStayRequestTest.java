package com.riaride.accounts.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): the guest name rejects every character that can break or spoof how the
// name is displayed, not only ASCII controls, and still accepts real names.
class RegisterStayRequestTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private static RegisterStayRequest withName(String guestName) {
		return new RegisterStayRequest(
			UUID.fromString("00000000-0000-0000-0000-000000000001"),
			guestName,
			LocalDate.of(2026, 12, 1),
			LocalDate.of(2026, 12, 5));
	}

	@Requirement("RIA-50")
	@ParameterizedTest(name = "U+{0}")
	@ValueSource(strings = {
		"0000", // NUL
		"001F", // last C0 control
		"007F", // DEL
		"0085", // NEL, a C1 control
		"009F", // last C1 control
		"2028", // line separator
		"2029", // paragraph separator
		"202A", // left-to-right embedding
		"202E", // right-to-left override
		"2066", // left-to-right isolate
		"2069" // pop directional isolate
	})
	void shouldRejectGuestNameWithControlSeparatorOrBidiCharacter(String codePoint) {
		var name = "Rita" + Character.toString(Integer.parseInt(codePoint, 16)) + "Almeida";

		var violations = validator.validate(withName(name));

		assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("guestName");
	}

	@Requirement("RIA-50")
	@ParameterizedTest
	@ValueSource(strings = {
		"Inês Gomes",
		"João D'Almeida-Gonçalves",
		"Zahra\u200CAhmadi", // ZWNJ is part of Persian spelling
		"Ελένη Παπαδοπούλου"
	})
	void shouldAcceptRealGuestNames(String name) {
		assertThat(validator.validate(withName(name))).isEmpty();
	}
}
