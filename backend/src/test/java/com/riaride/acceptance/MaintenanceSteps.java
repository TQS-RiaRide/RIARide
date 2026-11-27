package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.riaride.TestClock;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-92 (open a maintenance ticket). The operator logs in here, apart from the
// scenario's logged-in user, so a scenario can follow a guest's trip and the operator's ticket
// together. The hub, bike, trip and listing steps are in FleetSteps, VehicleRegistrationSteps and
// TripSteps.
public class MaintenanceSteps {

  private final ScenarioContext context;
  private final JdbcTemplate jdbc;
  private final KeycloakTestUsers users;
  private final TestClock clock;

  private String operatorToken;

  public MaintenanceSteps(
      ScenarioContext context, JdbcTemplate jdbc, KeycloakTestUsers users, TestClock clock) {
    this.context = context;
    this.jdbc = jdbc;
    this.users = users;
    this.clock = clock;
  }

  // Tickets reference bikes, so they go before the bikes (FleetSteps: before 7000, after 10000).
  // After hooks with a higher order run first.
  @Before(order = 6800)
  @After(order = 10500)
  public void removeTickets() {
    jdbc.update("delete from maintenance_ticket");
  }

  @When("the operator opens a ticket for bike {string} with the fault {string}")
  public void theOperatorOpensATicket(String code, String fault) {
    if (operatorToken == null) {
      operatorToken = users.logIn("Rui Operador", "OPERATOR", null);
    }
    context.setLastResponse(
        given()
            .auth()
            .oauth2(operatorToken)
            .contentType(ContentType.JSON)
            .body(Map.of("vehicleCode", code, "description", fault))
            .when()
            .post("/api/v1/tickets"));
  }

  @Then("the ticket for bike {string} is open and due {int} hours after it was opened")
  public void theTicketIsOpenAndDue(String code, int hours) {
    var ticket =
        jdbc.queryForMap(
            """
            select t.status, t.opened_at, t.sla_due_at
            from maintenance_ticket t join vehicle v on v.id = t.vehicle_id
            where v.code = ?
            """,
            code);
    var openedAt = ((Timestamp) ticket.get("opened_at")).toInstant();
    assertThat(ticket.get("status")).isEqualTo("OPEN");
    assertThat(openedAt).isEqualTo(clock.instant());
    assertThat(((Timestamp) ticket.get("sla_due_at")).toInstant())
        .isEqualTo(openedAt.plus(Duration.ofHours(hours)));
  }

  // The reservation the trip start made (RIA-54), cancelled once the ticket commits.
  @Then("the guest's booking of bike {string} is cancelled")
  public void theGuestsBookingIsCancelled(String code) {
    assertThat(
            jdbc.queryForList(
                "select status from reservation where vehicle_code = ?", String.class, code))
        .containsExactly("CANCELLED");
  }

  @Then("bike {string} has no ticket")
  public void bikeHasNoTicket(String code) {
    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from maintenance_ticket t join vehicle v on v.id = t.vehicle_id
                where v.code = ?
                """,
                Integer.class,
                code))
        .isZero();
  }
}
