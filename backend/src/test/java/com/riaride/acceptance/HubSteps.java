package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-56 (register a hub).
public class HubSteps {

  // Fields sent as JSON numbers, as a real client would; a value that is not a number is sent
  // as text, so the API's own validation reports it.
  private static final Set<String> NUMBER_FIELDS = Set.of("latitude", "longitude", "capacity");

  private final ScenarioContext context;
  private final KeycloakTestUsers users;
  private final JdbcTemplate jdbc;

  public HubSteps(ScenarioContext context, KeycloakTestUsers users, JdbcTemplate jdbc) {
    this.context = context;
    this.users = users;
    this.jdbc = jdbc;
  }

  // Scenarios share one database, so each starts without hubs. Cleanups run from the lowest
  // order up, children first: passes and payments (8000), guests (9000), hubs (9500), stays
  // (10000). Tables that reference hubs, such as bikes (RIA-57), need an order below 9500.
  @Before(order = 9500)
  public void removeHubs() {
    jdbc.update("delete from hub");
  }

  // Also used by RIA-57 (register a bike).
  @Given("an operator is logged in")
  public void anOperatorIsLoggedIn() {
    context.setAccessToken(users.logIn("Rui Operador", "OPERATOR", null));
  }

  @When("the user registers a hub with:")
  public void theUserRegistersAHubWith(Map<String, String> fields) {
    var body = new LinkedHashMap<String, Object>();
    fields.forEach((field, value) -> body.put(field, jsonValue(field, value)));
    context.setLastResponse(
        given()
            .auth()
            .oauth2(context.accessToken())
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/hubs"));
  }

  private static Object jsonValue(String field, String value) {
    if (!NUMBER_FIELDS.contains(field)) {
      return value;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException notANumber) {
      return value;
    }
  }

  @Then("the response has a {string} header pointing to the new hub")
  public void theResponseHasAHeaderPointingToTheNewHub(String header) {
    var response = context.lastResponse();
    var id = response.jsonPath().getString("id");
    assertThat(id).isNotBlank();
    assertThat(response.header(header)).endsWith("/api/v1/hubs/" + id);
  }

  @Then("the problem detail reports an invalid field {string}")
  public void theProblemDetailReportsAnInvalidField(String field) {
    var invalidFields =
        context.lastResponse().jsonPath().getList("'invalid-params'.name", String.class);
    assertThat(invalidFields).contains(field);
  }

  @Then("the fleet lists hub {string} with {int} slots")
  public void theFleetListsHubWithSlots(String name, int slots) {
    var capacities =
        jdbc.queryForList("select capacity from hub where name = ?", Integer.class, name);
    assertThat(capacities).containsExactly(slots);
  }

  @Then("the fleet does not list hub {string}")
  public void theFleetDoesNotListHub(String name) {
    var hubs = jdbc.queryForObject("select count(*) from hub where name = ?", Integer.class, name);
    assertThat(hubs).isZero();
  }
}
