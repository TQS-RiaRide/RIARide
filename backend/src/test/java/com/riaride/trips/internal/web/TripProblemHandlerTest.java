package com.riaride.trips.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.VehicleNotBookableException;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.trips.internal.application.NoActiveStayException;
import com.riaride.trips.internal.application.NoUsablePassException;
import com.riaride.trips.internal.application.ReservationHeldForAnotherBikeException;
import com.riaride.trips.internal.application.TripAlreadyInProgressException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

// Unit test (Surefire): starting a trip answers with RFC 9457 problem details (RIA-54). When two
// starts run at the same time, the database's unique indexes refuse the second one; that must be
// the same answer as the check that refuses it in the usual case. Details never repeat the SQL
// message, which holds ids and values (QA Manual 3.4).
class TripProblemHandlerTest {

  private static final String SQL_MESSAGE =
      "ERROR: duplicate key value violates unique constraint \"%s\" Detail: Key (pass_id)=(0ca7add9) already exists.";

  private final TripProblemHandler handler = new TripProblemHandler();

  // What Spring's exception translation produces when PostgreSQL rejects the insert.
  private static DataIntegrityViolationException violationOf(String constraint) {
    var sqlMessage = SQL_MESSAGE.formatted(constraint);
    var hibernate =
        new ConstraintViolationException(
            "could not execute statement [" + sqlMessage + "]",
            new SQLException(sqlMessage, "23505"),
            constraint);
    return new DataIntegrityViolationException(
        "could not execute statement [" + sqlMessage + "]", hibernate);
  }

  @Requirement("RIA-54")
  @ParameterizedTest(name = "{0} → {1} {2}")
  @CsvSource({
    "uq_trip_active_per_pass, 409, Trip already in progress",
    "uq_reservation_active_per_pass, 409, Trip already in progress",
    "uq_reservation_active_per_vehicle, 409, Bike not available"
  })
  void shouldAnswerEachConcurrentStartConstraintWithItsProblem(
      String constraint, int status, String title) {
    var problem = handler.dataIntegrityViolation(violationOf(constraint));

    assertThat(problem.getStatus()).isEqualTo(status);
    assertThat(problem.getTitle()).isEqualTo(title);
    assertThat(problem.getDetail()).doesNotContain("0ca7add9", "duplicate key");
  }

  // Any other constraint is a bug, not a conflict the guest can resolve: a 500 without the SQL.
  @Requirement("RIA-54")
  @Test
  void shouldAnswerAnUnknownConstraintAsAnInternalError() {
    var problem = handler.dataIntegrityViolation(violationOf("trip_vehicle_id_fkey"));

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail())
        .doesNotContain("0ca7add9", "duplicate key", "trip_vehicle_id_fkey");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerAViolationWithoutAConstraintNameAsAnInternalError() {
    var problem =
        handler.dataIntegrityViolation(
            new DataIntegrityViolationException(SQL_MESSAGE.formatted("")));

    assertThat(problem.getStatus()).isEqualTo(500);
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerTripAlreadyInProgress() {
    assertProblem(
        handler.tripAlreadyInProgress(new TripAlreadyInProgressException()),
        409,
        "Trip already in progress");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerReservationHeldForAnotherBike() {
    assertProblem(
        handler.reservationHeldForAnotherBike(new ReservationHeldForAnotherBikeException()),
        409,
        "Reservation held for another bike");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerBikeNotAvailable() {
    assertProblem(
        handler.bikeNotAvailable(new VehicleNotBookableException()), 409, "Bike not available");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerBikeNotFound() {
    assertProblem(handler.bikeNotFound(new VehicleNotFoundException()), 404, "Bike not found");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerNoValidPass() {
    assertProblem(handler.noValidPass(new NoUsablePassException()), 403, "No valid pass");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerNoActiveStay() {
    assertProblem(handler.noActiveStay(new NoActiveStayException()), 403, "No active stay");
  }

  @Requirement("RIA-54")
  @Test
  void shouldAnswerTripNotFound() {
    assertProblem(handler.tripNotFound(new TripNotFoundException()), 404, "Trip not found");
  }

  private static void assertProblem(ProblemDetail problem, int status, String title) {
    assertThat(problem.getStatus()).isEqualTo(status);
    assertThat(problem.getTitle()).isEqualTo(title);
    assertThat(problem.getDetail()).isNotBlank();
  }
}
