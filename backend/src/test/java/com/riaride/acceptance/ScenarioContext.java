package com.riaride.acceptance;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

// State shared between step definition classes within one scenario.
// A new instance is created for each scenario.
@Component
@ScenarioScope
public class ScenarioContext {

  private Response lastResponse;

  private String accessToken;

  public Response lastResponse() {
    if (lastResponse == null) {
      throw new IllegalStateException("No request was sent in this scenario");
    }
    return lastResponse;
  }

  public void setLastResponse(Response response) {
    this.lastResponse = response;
  }

  public String accessToken() {
    if (accessToken == null) {
      throw new IllegalStateException("No user is logged in in this scenario");
    }
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
