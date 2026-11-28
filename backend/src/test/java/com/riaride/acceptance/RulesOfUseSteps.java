package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.riaride.TestClock;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-96 (set the rules of use). The hotel manager acts through the API with a token of
// their own, so the scenario's logged-in guest keeps theirs.
public class RulesOfUseSteps {

  private static final String SEEDED_RULE = "00000000-0000-0000-0000-000000000096";

  private final KeycloakTestUsers users;
  private final JdbcTemplate jdbc;
  private final TestClock clock;

  public RulesOfUseSteps(KeycloakTestUsers users, JdbcTemplate jdbc, TestClock clock) {
    this.users = users;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  // Scenarios share one database and the TestClock starts each one at the same instant, so each
  // starts with only the seeded rule (3 h).
  @Before
  public void keepOnlyTheSeededRule() {
    jdbc.update("delete from usage_rule where id <> ?::uuid", SEEDED_RULE);
  }

  private String managerOf(String hotelName) {
    var hotelId =
        jdbc.queryForObject("select id::text from hotel where name = ?", String.class, hotelName);
    return users.logIn("Gestor " + hotelName, "HOTEL_MANAGER", hotelId);
  }

  @Given("the hotel manager of {string} has set the maximum trip duration to {int} hours")
  public void theHotelManagerHasSetTheMaximumTripDuration(String hotelName, int hours) {
    given()
        .auth()
        .oauth2(managerOf(hotelName))
        .contentType(ContentType.JSON)
        .body(Map.of("maxTripDurationMinutes", hours * 60))
        .when()
        .put("/api/v1/usage-rules")
        .then()
        .statusCode(200);
  }

  @Given("the hotel manager of {string} has changed the capacity of hub {string} to {int}")
  public void theHotelManagerHasChangedTheCapacity(String hotelName, String hubName, int capacity) {
    var hubId =
        jdbc.queryForObject("select id::text from hub where name = ?", String.class, hubName);
    given()
        .auth()
        .oauth2(managerOf(hotelName))
        .contentType(ContentType.JSON)
        .body(Map.of("capacity", capacity))
        .when()
        .put("/api/v1/hubs/{id}/capacity", hubId)
        .then()
        .statusCode(200);
  }

  @Given("{int} more minutes pass")
  public void moreMinutesPass(int minutes) {
    clock.advance(Duration.ofMinutes(minutes));
  }

  // The charge runs after the trip's end commits, on another thread. Its publication is stored with
  // the end and deleted once the listener succeeds (completion-mode: delete), so once it is gone
  // the
  // listener has run, and it recorded no overtime payment.
  @Then("the guest is not charged overtime")
  public void theGuestIsNotChargedOvertime() {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() ->
            assertThat(jdbc.queryForObject("select count(*) from event_publication", Integer.class))
                .isZero());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment where kind = 'OVERTIME'", Integer.class))
        .isZero();
  }
}
