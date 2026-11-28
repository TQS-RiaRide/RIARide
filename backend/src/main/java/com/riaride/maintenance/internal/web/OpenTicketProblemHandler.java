package com.riaride.maintenance.internal.web;

import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.VehicleRetiredException;
import com.riaride.maintenance.internal.domain.InvalidTicketException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps ticket errors (RIA-92) to RFC 9457 problem details. A 400 names the invalid field in
// "invalid-params" (RFC 9457, section 3); details never repeat the submitted values. Ordered first
// so it handles unreadable bodies before Spring Boot's default handler.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OpenTicketController.class)
class OpenTicketProblemHandler {

  static final String INVALID_TICKET = "Invalid ticket";
  static final String INVALID_PARAMS = "invalid-params";

  @ExceptionHandler(InvalidTicketException.class)
  ProblemDetail invalidTicket(InvalidTicketException exception) {
    var problem =
        problem(HttpStatus.BAD_REQUEST, INVALID_TICKET, "The ticket has an invalid field.");
    problem.setProperty(
        INVALID_PARAMS,
        List.of(Map.of("name", exception.getField(), "reason", exception.getMessage())));
    return problem;
  }

  // Jackson's message quotes the value, so it is not returned.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
    return problem(HttpStatus.BAD_REQUEST, INVALID_TICKET, "The request body is not valid JSON.");
  }

  @ExceptionHandler(VehicleNotFoundException.class)
  ProblemDetail vehicleNotFound(VehicleNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Vehicle not found", "No bike has this code.");
  }

  @ExceptionHandler(VehicleRetiredException.class)
  ProblemDetail vehicleRetired(VehicleRetiredException exception) {
    return problem(HttpStatus.CONFLICT, "Ticket refused", exception.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
