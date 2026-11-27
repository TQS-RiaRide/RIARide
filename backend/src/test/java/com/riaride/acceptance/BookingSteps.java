package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.riaride.TestClock;
import com.riaride.trips.internal.application.ReservationExpiryJob;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-90 (book a bike in advance), against the API and the database. Time moves only
// when a step moves the TestClock; the step then runs ReservationExpiryJob at once, as the
// scheduler would on the same clock. Trips and reservations are removed by TripSteps.
public class BookingSteps {

  private final ScenarioContext context;
  private final JdbcTemplate jdbc;
  private final TestClock clock;
  private final ReservationExpiryJob reservationExpiryJob;

  public BookingSteps(
      ScenarioContext context,
      JdbcTemplate jdbc,
      TestClock clock,
      ReservationExpiryJob reservationExpiryJob) {
    this.context = context;
    this.jdbc = jdbc;
    this.clock = clock;
    this.reservationExpiryJob = reservationExpiryJob;
  }

  @When("the guest books bike {string}")
  public void theGuestBooksBike(String code) {
    context.setLastResponse(book(code));
  }

  @Given("the guest booked bike {string}")
  public void theGuestBookedBike(String code) {
    assertThat(book(code).statusCode()).isEqualTo(201);
  }

  // BR11: the booking is active and ends the given number of minutes after it was made.
  @Then("the booking holds bike {string} for {int} minutes")
  public void theBookingHoldsBikeForMinutes(String code, int minutes) {
    var booking = theBooking();
    assertThat(booking.get("vehicle_code")).isEqualTo(code);
    assertThat(booking.get("status")).isEqualTo("ACTIVE");
    var createdAt = ((Timestamp) booking.get("created_at")).toInstant();
    assertThat(((Timestamp) booking.get("expires_at")).toInstant())
        .isEqualTo(createdAt.plus(Duration.ofMinutes(minutes)));
  }

  @When("{int} minutes pass without a trip starting on bike {string}")
  public void minutesPassWithoutATripStartingOnBike(int minutes, String code) {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from trip where vehicle_code = ?", Integer.class, code))
        .isZero();
    clock.advance(Duration.ofMinutes(minutes));
    reservationExpiryJob.expireOverdue();
  }

  @Then("the booking has expired")
  public void theBookingHasExpired() {
    assertThat(theBooking().get("status")).isEqualTo("EXPIRED");
  }

  // The trip was started from the booking: no second reservation was made for it.
  @Then("the trip uses the booking")
  public void theTripUsesTheBooking() {
    var booking = theBooking();
    assertThat(jdbc.queryForList("select reservation_id from trip", UUID.class))
        .containsExactly((UUID) booking.get("id"));
  }

  // The scenario's only booking.
  private Map<String, Object> theBooking() {
    return jdbc.queryForMap(
        "select id, vehicle_code, status, created_at, expires_at from reservation");
  }

  private Response book(String code) {
    return given()
        .auth()
        .oauth2(context.accessToken())
        .contentType(ContentType.JSON)
        .body(Map.of("vehicleCode", code))
        .when()
        .post("/api/v1/reservations");
  }
}
