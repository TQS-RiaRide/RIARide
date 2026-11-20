package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.EmailAlreadyRegisteredException;
import com.riaride.accounts.internal.application.IdentityProviderUnavailableException;
import com.riaride.accounts.internal.application.InvalidStayCodeException;
import com.riaride.accounts.internal.domain.StayAlreadyEndedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps sign-up errors to RFC 9457 problem details. Details never include a stay code,
// a password or whether a stay code exists (QA Manual 3.4).
@RestControllerAdvice(assignableTypes = GuestController.class)
class GuestProblemHandler {

	private static final Logger log = LoggerFactory.getLogger(GuestProblemHandler.class);

	@ExceptionHandler(InvalidStayCodeException.class)
	ProblemDetail invalidStayCode(InvalidStayCodeException exception) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid stay code", exception.getMessage());
	}

	@ExceptionHandler(StayAlreadyEndedException.class)
	ProblemDetail stayAlreadyEnded(StayAlreadyEndedException exception) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Stay already ended",
			"This stay has already ended, so its code can no longer be used to create an account.");
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail emailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
		return problem(HttpStatus.CONFLICT, "Email already registered", exception.getMessage());
	}

	// In practice, a concurrent sign-up took the same stay code or email between the checks and
	// the insert; the Keycloak user was already deleted. The SQL message holds the values, so it
	// is neither returned nor logged.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
		log.warn("Guest sign-up rejected by a database constraint ({})",
			exception.getMostSpecificCause().getClass().getSimpleName());
		return problem(HttpStatus.CONFLICT, "Conflict",
			"The account conflicts with another sign-up made at the same time. Try again.");
	}

	@ExceptionHandler(IdentityProviderUnavailableException.class)
	ProblemDetail identityProviderUnavailable(IdentityProviderUnavailableException exception) {
		log.error("Guest sign-up failed: {}", exception.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
			"The account could not be created right now. Try again later.");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
