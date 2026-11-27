package com.riaride.passes.internal.web;

import com.riaride.passes.internal.application.UsageRuleChangedConcurrentlyException;
import com.riaride.passes.internal.domain.InvalidUsageRuleException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

// Maps rule-of-use errors (RIA-96) to RFC 9457 problem details. A 400 names each invalid field in
// "invalid-params" (RFC 9457, section 3), as for hubs. Details never repeat the submitted values.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = UsageRuleController.class)
class UsageRuleProblemHandler {

  static final String INVALID_RULE = "Invalid rule of use";
  static final String INVALID_PARAMS = "invalid-params";
  private static final String DETAIL = "The rules of use are not valid.";

  @ExceptionHandler(InvalidUsageRuleException.class)
  ProblemDetail invalidRule(InvalidUsageRuleException exception) {
    return invalid(List.of(invalidParam(exception.getField(), exception.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidRequest(MethodArgumentNotValidException exception) {
    return invalid(
        exception.getBindingResult().getFieldErrors().stream()
            .map(error ->
                invalidParam(
                    error.getField(),
                    Objects.requireNonNullElse(
                        error.getDefaultMessage(), "The value is not valid.")))
            .toList());
  }

  // Jackson rejected a value, such as a duration that is not a whole number, or the body is not
  // JSON at all. Jackson's message quotes the value, so it is not returned.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
    if (exception.getMostSpecificCause() instanceof JacksonException jackson
        && !jackson.getPath().isEmpty()
        && jackson.getPath().getLast().getPropertyName() != null) {
      return invalid(
          List.of(
              invalidParam(
                  jackson.getPath().getLast().getPropertyName(),
                  "The value does not have the expected type or format.")));
    }
    return problem(HttpStatus.BAD_REQUEST, INVALID_RULE, "The request body is not valid JSON.");
  }

  @ExceptionHandler(UsageRuleChangedConcurrentlyException.class)
  ProblemDetail changedConcurrently(UsageRuleChangedConcurrentlyException exception) {
    return problem(
        HttpStatus.CONFLICT, "Rules of use changed concurrently", exception.getMessage());
  }

  private static Map<String, String> invalidParam(String name, String reason) {
    return Map.of("name", name, "reason", reason);
  }

  private static ProblemDetail invalid(List<Map<String, String>> params) {
    var problem = problem(HttpStatus.BAD_REQUEST, INVALID_RULE, DETAIL);
    problem.setProperty(INVALID_PARAMS, params);
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
