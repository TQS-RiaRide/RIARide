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

	public Response lastResponse() {
		if (lastResponse == null) {
			throw new IllegalStateException("No request was sent in this scenario");
		}
		return lastResponse;
	}

	public void setLastResponse(Response response) {
		this.lastResponse = response;
	}

}
