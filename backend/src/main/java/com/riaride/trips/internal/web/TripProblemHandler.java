package com.riaride.trips.internal.web;

import com.riaride.fleet.VehicleNotBookableException;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.trips.internal.application.NoActiveStayException;
import com.riaride.trips.internal.application.NoUsablePassException;
import com.riaride.trips.internal.application.ReservationHeldForAnotherBikeException;
import com.riaride.trips.internal.application.TripAlreadyInProgressException;
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

// Maps the refusals of starting a trip to RFC 9457 problem details (RIA-54). When two starts run at
// the same time, a unique index refuses the second one; it gets the same answer as the check that
// refuses it in the usual case. Details never repeat the SQL message, which holds ids and values.
@RestControllerAdvice(assignableTypes = TripController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class TripProblemHandler {

	private static final Logger log = LoggerFactory.getLogger(TripProblemHandler.class);

	static final String TRIP_ACTIVE_PER_PASS = "uq_trip_active_per_pass";
	static final String RESERVATION_ACTIVE_PER_PASS = "uq_reservation_active_per_pass";
	static final String RESERVATION_ACTIVE_PER_VEHICLE = "uq_reservation_active_per_vehicle";

	@ExceptionHandler(TripAlreadyInProgressException.class)
	ProblemDetail tripAlreadyInProgress(TripAlreadyInProgressException exception) {
		return problem(HttpStatus.CONFLICT, "Trip already in progress", exception.getMessage());
	}

	@ExceptionHandler(ReservationHeldForAnotherBikeException.class)
	ProblemDetail reservationHeldForAnotherBike(ReservationHeldForAnotherBikeException exception) {
		return problem(HttpStatus.CONFLICT, "Reservation held for another bike", exception.getMessage());
	}

	@ExceptionHandler(VehicleNotBookableException.class)
	ProblemDetail bikeNotAvailable(VehicleNotBookableException exception) {
		return problem(HttpStatus.CONFLICT, "Bike not available", exception.getMessage());
	}

	@ExceptionHandler(VehicleNotFoundException.class)
	ProblemDetail bikeNotFound(VehicleNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Bike not found", exception.getMessage());
	}

	@ExceptionHandler(NoUsablePassException.class)
	ProblemDetail noValidPass(NoUsablePassException exception) {
		return problem(HttpStatus.FORBIDDEN, "No valid pass", exception.getMessage());
	}

	@ExceptionHandler(NoActiveStayException.class)
	ProblemDetail noActiveStay(NoActiveStayException exception) {
		return problem(HttpStatus.FORBIDDEN, "No active stay", exception.getMessage());
	}

	@ExceptionHandler(TripNotFoundException.class)
	ProblemDetail tripNotFound(TripNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Trip not found", exception.getMessage());
	}

	// A concurrent start passed the checks and was refused by a unique index. Any other constraint
	// is unexpected: a 500, so it is not mistaken for a conflict the guest can resolve.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
		var constraint = violatedConstraint(exception);
		log.warn("Trip start rejected by database constraint {}", constraint);
		if (TRIP_ACTIVE_PER_PASS.equalsIgnoreCase(constraint) || RESERVATION_ACTIVE_PER_PASS.equalsIgnoreCase(constraint)) {
			return tripAlreadyInProgress(new TripAlreadyInProgressException());
		}
		if (RESERVATION_ACTIVE_PER_VEHICLE.equalsIgnoreCase(constraint)) {
			return bikeNotAvailable(new VehicleNotBookableException());
		}
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
			"The trip could not be started right now. Try again later.");
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
