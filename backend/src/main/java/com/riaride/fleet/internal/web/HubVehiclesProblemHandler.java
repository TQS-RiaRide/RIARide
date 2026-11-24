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

  // A path or query parameter that cannot be converted: a hub id that is not a UUID, an unknown
  // vehicle type (RIA-53), or any parameter added later. The value is not repeated in the detail.
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail invalidArgument(MethodArgumentTypeMismatchException exception) {
    var parameter = exception.getName();
    return switch (parameter) {
      case "hubId" ->
          problem(HttpStatus.BAD_REQUEST, "Invalid hub id", "The hub id must be a UUID.");
      case "type" ->
          invalid(
              "Invalid vehicle type",
              parameter,
              "The type must be one of: "
                  + Arrays.stream(VehicleType.values())
                      .map(Enum::name)
                      .collect(Collectors.joining(", "))
                  + ".");
      default ->
          invalid(
              "Invalid parameter",
              parameter,
              "The value does not have the expected type or format.");
    };
  }

  private static ProblemDetail invalid(String title, String parameter, String reason) {
    var problem = problem(HttpStatus.BAD_REQUEST, title, reason);
    problem.setProperty("invalid-params", List.of(Map.of("name", parameter, "reason", reason)));
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
