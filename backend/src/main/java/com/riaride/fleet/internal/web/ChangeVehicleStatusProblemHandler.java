package com.riaride.fleet.internal.web;

import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.internal.domain.InvalidVehicleException;
import com.riaride.fleet.internal.domain.VehicleStatusChangeRefusedException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

// Maps bike status change errors (RIA-91) to RFC 9457 problem details. A 400 names the invalid
// field in "invalid-params" (RFC 9457, section 3); details never repeat the submitted values.
// Ordered first so it handles unreadable bodies before Spring Boot's default handler.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ChangeVehicleStatusController.class)
class ChangeVehicleStatusProblemHandler {

  static final String INVALID_STATUS_CHANGE = "Invalid status change";
  static final String INVALID_PARAMS = "invalid-params";

  @ExceptionHandler(InvalidVehicleException.class)
  ProblemDetail invalidVehicle(InvalidVehicleException exception) {
    return invalid(exception.getField(), exception.getMessage());
  }

  // Jackson rejected a value, such as an unknown status, or the body is not JSON at all. Jackson's
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
    return problem(
        HttpStatus.BAD_REQUEST, INVALID_STATUS_CHANGE, "The request body is not valid JSON.");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail invalidVehicleId(MethodArgumentTypeMismatchException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid vehicle id", "The vehicle id must be a UUID.");
  }

  @ExceptionHandler(VehicleNotFoundException.class)
  ProblemDetail vehicleNotFound(VehicleNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Vehicle not found", "No bike has this id.");
  }

  @ExceptionHandler(VehicleStatusChangeRefusedException.class)
  ProblemDetail statusChangeRefused(VehicleStatusChangeRefusedException exception) {
    return problem(HttpStatus.CONFLICT, "Status change refused", exception.getMessage());
  }

  private static ProblemDetail invalid(String field, String reason) {
    var problem =
        problem(
            HttpStatus.BAD_REQUEST, INVALID_STATUS_CHANGE, "The requested status is not valid.");
    problem.setProperty(INVALID_PARAMS, List.of(Map.of("name", field, "reason", reason)));
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
