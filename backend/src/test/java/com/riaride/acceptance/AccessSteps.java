package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.util.Arrays;

// Steps of RIA-86 (log in and see only the features of my role).
public class AccessSteps {

  private final ScenarioContext context;
  private final KeycloakTestUsers users;

  public AccessSteps(ScenarioContext context, KeycloakTestUsers users) {
    this.context = context;
    this.users = users;
  }

  @Given("a user {string} with the {string} role is logged in")
  public void aUserWithTheRoleIsLoggedIn(String fullName, String role) {
    context.setAccessToken(users.logIn(fullName, role, null));
  }

  @When("the user opens their account")
  public void theUserOpensTheirAccount() {
    context.setLastResponse(given().auth().oauth2(context.accessToken()).when().get("/api/v1/me"));
  }

  @When("the user sends POST {string} with an empty body")
  public void theUserSendsPostWithAnEmptyBody(String path) {
    context.setLastResponse(
        given()
            .auth()
            .oauth2(context.accessToken())
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post(path));
  }

  @Then("the user's workspaces are {string}")
  public void theUsersWorkspacesAre(String workspaces) {
    var expected = Arrays.stream(workspaces.split(",")).map(String::trim).toList();
    assertThat(context.lastResponse().jsonPath().getList("workspaces", String.class))
        .containsExactlyElementsOf(expected);
  }

  @Then("the user does not see the {string} workspace")
  public void theUserDoesNotSeeTheWorkspace(String workspace) {
    assertThat(context.lastResponse().jsonPath().getList("workspaces", String.class))
        .doesNotContain(workspace);
  }
}
