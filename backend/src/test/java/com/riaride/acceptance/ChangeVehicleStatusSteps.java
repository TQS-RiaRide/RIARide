package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-91 (change a bike's state). The hub, bike and listing steps are in FleetSteps and
// VehicleRegistrationSteps.
public class ChangeVehicleStatusSteps {

  private final ScenarioContext context;
  private final JdbcTemplate jdbc;

  public ChangeVehicleStatusSteps(ScenarioContext context, JdbcTemplate jdbc) {
    this.context = context;
    this.jdbc = jdbc;
  }

  @When("the operator retires bike {string}")
  public void theOperatorRetiresBike(String code) {
    var vehicleId = jdbc.queryForObject("select id from vehicle where code = ?", UUID.class, code);
    context.setLastResponse(
        given()
            .auth()
            .oauth2(context.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("status", "RETIRED"))
            .when()
            .put("/api/v1/vehicles/{id}/status", vehicleId));
  }

  @Then("bike {string} is not listed")
  public void bikeIsNotListed(String code) {
    assertThat(listedCodes()).doesNotContain(code);
  }

  @Then("bike {string} is listed")
  public void bikeIsListed(String code) {
    assertThat(listedCodes()).contains(code);
  }

  private List<String> listedCodes() {
    var response = context.lastResponse();
    assertThat(response.statusCode()).isEqualTo(200);
    return response.jsonPath().getList("code", String.class);
  }
}
