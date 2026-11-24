package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

// Steps shared by all features. Story-specific steps live in their own
// classes in this package, added by each story.
public class CommonSteps {

  private final ScenarioContext context;

  public CommonSteps(ScenarioContext context) {
    this.context = context;
  }

  @When("a client sends GET {string}")
  public void aClientSendsGet(String path) {
    context.setLastResponse(given().when().get(path));
  }

  @Then("the response status is {int}")
  public void theResponseStatusIs(int status) {
    assertThat(context.lastResponse().statusCode()).isEqualTo(status);
  }

  @Then("the response field {string} is {string}")
  public void theResponseFieldIs(String jsonPath, String expected) {
    assertThat(context.lastResponse().jsonPath().getString(jsonPath)).isEqualTo(expected);
  }

  // RFC 9457: errors are problem details with no internal data.
  @Then("the response is a problem detail with status {int}")
  public void theResponseIsAProblemDetailWithStatus(int status) {
    var response = context.lastResponse();
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(response.jsonPath().getInt("status")).isEqualTo(status);
    assertThat(response.jsonPath().getString("title")).isNotBlank();
  }
}
