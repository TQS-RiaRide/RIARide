package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): how VehicleProblemHandler maps a vehicle refused by a database constraint
// (RIA-57). The unique index on the code answers 409 "Vehicle code already in use", like the check
// before the insert; any other constraint answers a generic 409. The SQL message holds the
// submitted values, so it never reaches the response.
class VehicleProblemHandlerTest {

  private static final String SQL_MESSAGE =
      "duplicate key value violates unique constraint: (lower(code))=(eb-401)";

  private final VehicleProblemHandler handler = new VehicleProblemHandler();

  private static DataIntegrityViolationException violationOf(String constraint) {
    var cause =
        new ConstraintViolationException(
            SQL_MESSAGE, new SQLException(SQL_MESSAGE, "23505"), constraint);
    return new DataIntegrityViolationException("could not execute statement", cause);
  }

  @Requirement("RIA-57")
  @Test
  void shouldMapTheVehicleCodeConstraintToCodeAlreadyInUse() {
    var problem = handler.dataIntegrityViolation(violationOf("uq_vehicle_code"));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Vehicle code already in use");
    assertThat(problem.getDetail()).doesNotContain("eb-401");
  }

  // PostgreSQL may report the name in another case.
  @Requirement("RIA-57")
  @Test
  void shouldMatchTheVehicleCodeConstraintIgnoringCase() {
    var problem = handler.dataIntegrityViolation(violationOf("UQ_VEHICLE_CODE"));

    assertThat(problem.getTitle()).isEqualTo("Vehicle code already in use");
  }

  @Requirement("RIA-57")
  @Test
  void shouldMapAnyOtherConstraintToAGenericConflict() {
    var problem = handler.dataIntegrityViolation(violationOf("ck_vehicle_status"));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Conflict");
    assertThat(problem.getDetail()).doesNotContain("eb-401");
  }

  @Requirement("RIA-57")
  @Test
  void shouldMapAViolationWithoutAConstraintNameToAGenericConflict() {
    var problem =
        handler.dataIntegrityViolation(
            new DataIntegrityViolationException(
                "could not execute statement", new SQLException(SQL_MESSAGE)));

    assertThat(problem.getTitle()).isEqualTo("Conflict");
  }
}
