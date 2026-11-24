package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.HubNotFoundException;
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

  // The value is not repeated in the detail.
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail invalidHubId(MethodArgumentTypeMismatchException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid hub id", "The hub id must be a UUID.");
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
