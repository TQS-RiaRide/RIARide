package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.HubNameAlreadyInUseException;
import com.riaride.fleet.internal.domain.InvalidHubException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

// Maps hub registration errors to RFC 9457 problem details. A 400 names each invalid field in
// "invalid-params" (RFC 9457, section 3). Details never repeat the submitted values.
// Ordered first so it handles validation and unreadable bodies before Spring Boot's default
// handler.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = HubController.class)
class HubProblemHandler {

  private static final Logger log = LoggerFactory.getLogger(HubProblemHandler.class);

  static final String INVALID_HUB = "Invalid hub";
  static final String INVALID_PARAMS = "invalid-params";
  static final String HUB_NAME_CONSTRAINT = "uq_hub_name";

  @ExceptionHandler(InvalidHubException.class)
  ProblemDetail invalidHub(InvalidHubException exception) {
    return invalid(
        "The hub details are not valid.",
        List.of(invalidParam(exception.getField(), exception.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidRequest(MethodArgumentNotValidException exception) {
    var params =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error ->
                invalidParam(
                    error.getField(),
                    Objects.requireNonNullElse(
                        error.getDefaultMessage(), "The value is not valid.")))
            .toList();
    return invalid("The hub details are not valid.", params);
  }

  // Jackson rejected a value, such as an unknown kind or a capacity that is not a whole number,
  // or the body is not JSON at all. Jackson's message quotes the value, so it is not returned.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
    if (exception.getMostSpecificCause() instanceof JacksonException jackson
        && !jackson.getPath().isEmpty()
        && jackson.getPath().getLast().getPropertyName() != null) {
      var field = jackson.getPath().getLast().getPropertyName();
      return invalid(
          "The hub details are not valid.",
          List.of(invalidParam(field, "The value does not have the expected type or format.")));
    }
    return problem(HttpStatus.BAD_REQUEST, INVALID_HUB, "The request body is not valid JSON.");
  }

  @ExceptionHandler(HubNameAlreadyInUseException.class)
  ProblemDetail hubNameAlreadyInUse(HubNameAlreadyInUseException exception) {
    return problem(HttpStatus.CONFLICT, "Hub name already in use", exception.getMessage());
  }

  // A database constraint rejected the hub: in practice, a concurrent registration took the same
  // name between the check and the insert. The SQL message holds the values, so it is neither
  // returned nor logged.
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
    log.warn(
        "Hub registration rejected by a database constraint ({})",
        exception.getMostSpecificCause().getClass().getSimpleName());
    if (violatesHubNameConstraint(exception)) {
      return hubNameAlreadyInUse(new HubNameAlreadyInUseException());
    }
    return problem(
        HttpStatus.CONFLICT,
        "Conflict",
        "The hub conflicts with data registered at the same time. Try again.");
  }

  private static boolean violatesHubNameConstraint(Throwable exception) {
    for (var cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return HUB_NAME_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
      }
    }
    return false;
  }

  private static Map<String, String> invalidParam(String name, String reason) {
    return Map.of("name", name, "reason", reason);
  }

  private static ProblemDetail invalid(String detail, List<Map<String, String>> params) {
    var problem = problem(HttpStatus.BAD_REQUEST, INVALID_HUB, detail);
    problem.setProperty(INVALID_PARAMS, params);
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
