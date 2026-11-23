package com.riaride.accounts.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): stay codes have the expected format and do not repeat.
class StayCodeGeneratorTest {

	private static final String STAY_CODE_FORMAT = "^[A-HJ-NP-Z2-9]{8}$";

	@Requirement("RIA-50")
	@Test
	void shouldGenerateCodeWithEightUnambiguousUppercaseCharacters() {
		var generator = new StayCodeGenerator(new Random(42));

		var code = generator.next();

		assertThat(code.value()).matches(STAY_CODE_FORMAT);
	}

	@Requirement("RIA-50")
	@Test
	void shouldNeverUseCharactersThatAreEasyToConfuse() {
		var generator = new StayCodeGenerator(new Random(7));

		for (int i = 0; i < 1_000; i++) {
			assertThat(generator.next().value()).doesNotContain("0", "O", "1", "I");
		}
	}

	@Requirement("RIA-50")
	@Test
	void shouldGenerateTenThousandDistinctCodes() {
		var generator = new StayCodeGenerator(new Random(2026));
		Set<String> codes = new HashSet<>();

		for (int i = 0; i < 10_000; i++) {
			codes.add(generator.next().value());
		}

		assertThat(codes).hasSize(10_000);
	}

	@Requirement("RIA-50")
	@Test
	void shouldGenerateSameSequenceForSameSeed() {
		var first = new StayCodeGenerator(new Random(99));
		var second = new StayCodeGenerator(new Random(99));

		assertThat(first.next()).isEqualTo(second.next());
	}

}
