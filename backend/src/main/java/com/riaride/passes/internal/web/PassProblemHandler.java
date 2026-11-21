package com.riaride.passes.internal.web;

import com.riaride.passes.internal.application.IdempotencyKeyReusedException;
import com.riaride.passes.internal.application.NoActiveStayException;
import com.riaride.passes.internal.application.PassNotCreatedException;
import com.riaride.passes.internal.application.PassAlreadyOpenException;
import com.riaride.passes.internal.application.PassPriceNotSetException;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.TransientPaymentException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps pass purchase errors to RFC 9457 problem details. Details never repeat the payment
// provider's messages, card data or references.
@RestControllerAdvice(assignableTypes = PassController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PassProblemHandler {

	private static final Logger log = LoggerFactory.getLogger(PassProblemHandler.class);
	private static final String OPEN_PASS_CONSTRAINT = "uq_pass_open_per_guest";
	private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uq_payment_idempotency_key";

	@ExceptionHandler(NoActiveStayException.class)
	ProblemDetail noActiveStay(NoActiveStayException exception) {
		return problem(HttpStatus.FORBIDDEN, "No active stay", exception.getMessage());
	}

	@ExceptionHandler(PassAlreadyOpenException.class)
	ProblemDetail passAlreadyOpen(PassAlreadyOpenException exception) {
		return problem(HttpStatus.CONFLICT, "Pass already open", exception.getMessage());
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException exception) {
		return problem(HttpStatus.CONFLICT, "Idempotency key already used", exception.getMessage());
	}

	@ExceptionHandler(PermanentPaymentException.class)
	ProblemDetail paymentDeclined(PermanentPaymentException exception) {
		log.info("Pass purchase refused by the payment provider ({})", exception.reason());
		return problem(HttpStatus.PAYMENT_REQUIRED, "Payment declined",
			"The card was not charged and no pass was created. Try another card, with a new Idempotency-Key.");
	}

	@ExceptionHandler(TransientPaymentException.class)
	ProblemDetail paymentUnavailable(TransientPaymentException exception) {
		log.warn("Pass purchase could not reach the payment provider ({})", exception.reason());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment unavailable",
			"The payment could not be completed right now. Try again with the same Idempotency-Key.");
	}

	@ExceptionHandler(PassNotCreatedException.class)
	ProblemDetail passNotCreated(PassNotCreatedException exception) {
		log.error("Pass purchase failed after the payment ({})", exception.getCause().getClass().getSimpleName());
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Pass not created",
			"The pass could not be created, and the payment is being refunded. Try again later.");
	}

	@ExceptionHandler(PassPriceNotSetException.class)
	ProblemDetail passPriceNotSet(PassPriceNotSetException exception) {
		log.error("Pass purchase failed: {}", exception.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Pass prices not set", exception.getMessage());
	}

	// The open-pass and idempotency-key constraints are the final guards against concurrent
	// purchases. Any other constraint is unexpected. The SQL message holds values, so it is
	// neither returned nor logged.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
		var constraint = violatedConstraint(exception);
		log.warn("Pass purchase rejected by database constraint {}", constraint);
		if (OPEN_PASS_CONSTRAINT.equalsIgnoreCase(constraint)) {
			return passAlreadyOpen(new PassAlreadyOpenException());
		}
		if (IDEMPOTENCY_KEY_CONSTRAINT.equalsIgnoreCase(constraint)) {
			return problem(HttpStatus.CONFLICT, "Conflict",
				"The same purchase is being processed. Retry with the same Idempotency-Key to get its result.");
		}
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
			"The pass could not be bought right now. Try again later.");
	}

	private static String violatedConstraint(Throwable exception) {
		for (var cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
