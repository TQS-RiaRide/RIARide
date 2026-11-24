package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): when a concurrent registration takes the same name between the check and
// the insert, the unique constraint uq_hub_name rejects the insert. That must still be the 409
// "Hub name already in use" of the scenario, any other constraint a generic 409, and neither the
// problem detail nor the log may repeat the SQL message, which holds the submitted values
// (QA Manual 3.4).
@ExtendWith(OutputCaptureExtension.class)
class HubProblemHandlerTest {

  private static final String HUB_NAME = "Marés Praia – Beach Front";
  private static final String SQL_MESSAGE =
      "ERROR: duplicate key value violates unique constraint \"%s\" Detail: Key (name)=("
          + HUB_NAME
          + ") already exists.";

  private final HubProblemHandler handler = new HubProblemHandler();

  // What Spring's exception translation produces when PostgreSQL rejects the insert.
  private static DataIntegrityViolationException violationOf(String constraint) {
    var sqlMessage = SQL_MESSAGE.formatted(constraint);
    var sqlException = new SQLException(sqlMessage, "23505");
    var hibernate =
        new ConstraintViolationException(
            "could not execute statement [" + sqlMessage + "]", sqlException, constraint);
    return new DataIntegrityViolationException(
        "could not execute statement [" + sqlMessage + "]", hibernate);
  }

  @Requirement("RIA-56")
  @Test
  void shouldAnswerHubNameAlreadyInUseWhenConcurrentRegistrationViolatesUniqueName() {
    var problem = handler.dataIntegrityViolation(violationOf("uq_hub_name"));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Hub name already in use");
  }

  @Requirement("RIA-56")
  @Test
  void shouldAnswerGenericConflictWhenAnotherConstraintIsViolated() {
    var problem = handler.dataIntegrityViolation(violationOf("fk_hub_hotel"));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Conflict");
  }

  @Requirement("RIA-56")
  @Test
  void shouldAnswerGenericConflictWhenNoConstraintIsNamed() {
    var problem =
        handler.dataIntegrityViolation(
            new DataIntegrityViolationException(SQL_MESSAGE.formatted("")));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Conflict");
  }

  @Requirement("RIA-56")
  @Test
  void shouldNeitherReturnNorLogSqlMessageWhenUniqueNameIsViolated(CapturedOutput output) {
    var problem = handler.dataIntegrityViolation(violationOf("uq_hub_name"));

    assertThat(problem.getDetail())
        .isNotBlank()
        .doesNotContain(HUB_NAME, "uq_hub_name", "duplicate key", "Key (name)");
    assertThat(output.getAll()).doesNotContain(HUB_NAME, "duplicate key", "Key (name)");
  }

  @Requirement("RIA-56")
  @Test
  void shouldNeitherReturnNorLogSqlMessageWhenAnotherConstraintIsViolated(CapturedOutput output) {
    var problem = handler.dataIntegrityViolation(violationOf("fk_hub_hotel"));

    assertThat(problem.getDetail())
        .isNotBlank()
        .doesNotContain(HUB_NAME, "fk_hub_hotel", "duplicate key", "Key (name)");
    assertThat(output.getAll()).doesNotContain(HUB_NAME, "duplicate key", "Key (name)");
  }
}
