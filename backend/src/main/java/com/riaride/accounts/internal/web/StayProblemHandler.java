package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.HotelAccessDeniedException;
import com.riaride.accounts.internal.application.HotelNotFoundException;
import com.riaride.accounts.internal.application.StayCodeUnavailableException;
import com.riaride.accounts.internal.domain.InvalidStayDatesException;
import com.riaride.accounts.internal.domain.StayAlreadyEndedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps the accounts module's errors to RFC 9457 problem details.
// Details never include a stay code or a guest's name (QA Manual 3.4).
@RestControllerAdvice(assignableTypes = StayController.class)
class StayProblemHandler {

	private static final Logger log = LoggerFactory.getLogger(StayProblemHandler.class);

	@ExceptionHandler(HotelNotFoundException.class)
	ProblemDetail hotelNotFound(HotelNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Hotel not found", exception.getMessage());
	}

	@ExceptionHandler(HotelAccessDeniedException.class)
	ProblemDetail hotelAccessDenied(HotelAccessDeniedException exception) {
		return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
	}

	@ExceptionHandler(InvalidStayDatesException.class)
	ProblemDetail invalidStayDates(InvalidStayDatesException exception) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid stay dates", exception.getMessage());
	}

	@ExceptionHandler(StayAlreadyEndedException.class)
	ProblemDetail stayAlreadyEnded(StayAlreadyEndedException exception) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Stay already ended", exception.getMessage());
	}

	@ExceptionHandler(StayCodeUnavailableException.class)
	ProblemDetail stayCodeUnavailable(StayCodeUnavailableException exception) {
		log.error("Stay registration failed: {}", exception.getMessage());
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
			"The stay could not be registered. Try again later.");
	}

	// A database constraint rejected the stay: in practice, a concurrent registration took the
	// same stay code between the check and the insert. The client may retry. The exception
	// message holds SQL values, such as the stay code, so it is neither returned nor logged.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
		log.warn("Stay registration rejected by a database constraint ({})",
			exception.getMostSpecificCause().getClass().getSimpleName());
		return problem(HttpStatus.CONFLICT, "Conflict",
			"The stay conflicts with data registered at the same time. Try again.");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
