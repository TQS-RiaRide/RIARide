package com.riaride.accounts.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.internal.application.StayCodeUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): unexpected persistence errors become problem details that reveal
// nothing stored in the request or the database (QA Manual 3.4).
class StayProblemHandlerTest {

	private static final String LEAKY_MESSAGE =
		"ERROR: duplicate key value violates unique constraint \"uq_stay_stay_code\" "
			+ "Detail: Key (stay_code)=(K7M3Q9TX) already exists. guest_name=Rita Almeida";

	private final StayProblemHandler handler = new StayProblemHandler();

	@Requirement("RIA-50")
	@Test
	void shouldAnswerConflictWithoutDatabaseDetailsWhenAConstraintIsViolated() {
		var problem = handler.dataIntegrityViolation(new DataIntegrityViolationException(LEAKY_MESSAGE));

		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getTitle()).isEqualTo("Conflict");
		assertThat(problem.getDetail())
			.isNotBlank()
			.doesNotContain("K7M3Q9TX", "Rita Almeida", "uq_stay_stay_code", "duplicate key");
	}

	@Requirement("RIA-50")
	@Test
	void shouldAnswerServerErrorWithoutInternalDetailsWhenNoCodeIsFree() {
		var problem = handler.stayCodeUnavailable(new StayCodeUnavailableException(5));

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getDetail()).doesNotContain("attempts", "5");
	}

}
