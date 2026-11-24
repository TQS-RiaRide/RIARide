package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.HubFullException;
import com.riaride.fleet.internal.application.HubNotFoundException;
import com.riaride.fleet.internal.application.VehicleCodeAlreadyInUseException;
import com.riaride.fleet.internal.domain.InvalidVehicleException;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

// Maps vehicle registration errors to RFC 9457 problem details. A 400 names each invalid field in
// "invalid-params" (RFC 9457, section 3). Details never repeat the submitted values.
// Ordered first so it handles unreadable bodies before Spring Boot's default handler.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RegisterVehicleController.class)
class VehicleProblemHandler {

  private static final Logger log = LoggerFactory.getLogger(VehicleProblemHandler.class);

  static final String INVALID_VEHICLE = "Invalid vehicle";
  static final String INVALID_PARAMS = "invalid-params";
  static final String VEHICLE_CODE_CONSTRAINT = "uq_vehicle_code";

  @ExceptionHandler(InvalidVehicleException.class)
  ProblemDetail invalidVehicle(InvalidVehicleException exception) {
    return invalid(exception.getField(), exception.getMessage());
  }

  // Jackson rejected a value, such as an unknown type, or the body is not JSON at all. Jackson's
  // message quotes the value, so it is not returned.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
    if (exception.getMostSpecificCause() instanceof JacksonException jackson
        && !jackson.getPath().isEmpty()
        && jackson.getPath().getLast().getPropertyName() != null) {
      return invalid(
          jackson.getPath().getLast().getPropertyName(),
          "The value does not have the expected type or format.");
    }
    return problem(HttpStatus.BAD_REQUEST, INVALID_VEHICLE, "The request body is not valid JSON.");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail invalidHubId(MethodArgumentTypeMismatchException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid hub id", "The hub id must be a UUID.");
  }

  @ExceptionHandler(HubNotFoundException.class)
  ProblemDetail hubNotFound(HubNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Hub not found", exception.getMessage());
  }

  @ExceptionHandler(VehicleCodeAlreadyInUseException.class)
  ProblemDetail vehicleCodeAlreadyInUse(VehicleCodeAlreadyInUseException exception) {
    return problem(HttpStatus.CONFLICT, "Vehicle code already in use", exception.getMessage());
  }

  @ExceptionHandler(HubFullException.class)
  ProblemDetail hubFull(HubFullException exception) {
    return problem(HttpStatus.CONFLICT, "Hub full", exception.getMessage());
  }

  // A database constraint rejected the vehicle: in practice, a concurrent registration took the
  // same code between the check and the insert. The SQL message holds the values, so it is
  // neither returned nor logged.
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
    log.warn(
        "Vehicle registration rejected by a database constraint ({})",
        exception.getMostSpecificCause().getClass().getSimpleName());
    if (violatesVehicleCodeConstraint(exception)) {
      return vehicleCodeAlreadyInUse(new VehicleCodeAlreadyInUseException());
    }
    return problem(
        HttpStatus.CONFLICT,
        "Conflict",
        "The vehicle conflicts with data registered at the same time. Try again.");
  }

  private static boolean violatesVehicleCodeConstraint(Throwable exception) {
    for (var cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return VEHICLE_CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
      }
    }
    return false;
  }

  private static ProblemDetail invalid(String field, String reason) {
    var problem =
        problem(HttpStatus.BAD_REQUEST, INVALID_VEHICLE, "The vehicle details are not valid.");
    problem.setProperty(INVALID_PARAMS, List.of(Map.of("name", field, "reason", reason)));
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
