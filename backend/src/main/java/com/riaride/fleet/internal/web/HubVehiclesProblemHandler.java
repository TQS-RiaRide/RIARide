package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.HubNotFoundException;
import com.riaride.fleet.internal.domain.VehicleType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Maps errors of the hub vehicles listing to RFC 9457 problem details.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = HubVehiclesController.class)
class HubVehiclesProblemHandler {

	@ExceptionHandler(HubNotFoundException.class)
	ProblemDetail hubNotFound(HubNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Hub not found", exception.getMessage());
	}

	// A hub id that is not a UUID, or an unknown vehicle type (RIA-53). The value is not repeated
	// in the detail.
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail invalidArgument(MethodArgumentTypeMismatchException exception) {
		if ("type".equals(exception.getName())) {
			var reason = "The type must be one of: " + Arrays.stream(VehicleType.values())
				.map(Enum::name).collect(Collectors.joining(", ")) + ".";
			var problem = problem(HttpStatus.BAD_REQUEST, "Invalid vehicle type", reason);
			problem.setProperty("invalid-params", List.of(Map.of("name", "type", "reason", reason)));
			return problem;
		}
		return problem(HttpStatus.BAD_REQUEST, "Invalid hub id", "The hub id must be a UUID.");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
