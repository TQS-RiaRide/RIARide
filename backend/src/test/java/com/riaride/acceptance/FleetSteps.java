package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.riaride.TestMosquitto;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for the fleet's vehicles and their telemetry (RIA-59), reusable by RIA-53 and RIA-58.
public class FleetSteps {

	// The telemetry lag bound of RIA-58.
	private static final Duration TELEMETRY_LAG = Duration.ofSeconds(5);

	private final ScenarioContext context;
	private final JdbcTemplate jdbc;

	public FleetSteps(ScenarioContext context, JdbcTemplate jdbc) {
		this.context = context;
		this.jdbc = jdbc;
	}

	// Before the hubs are removed (HubSteps, default order): vehicles reference hubs.
	// Also after each scenario, so no vehicle is left to block a later "delete from hub".
	@Before(order = 7000)
	@After
	public void removeVehicles() {
		jdbc.update("delete from vehicle");
	}

	// A hub at the hotel of the same name, with one available e-bike docked there. Hub names are
	// unique, so a second bike at a hub the scenario already has is docked at that hub (RIA-54).
	@Given("a hub {string} with an available bike {string} with {int} % battery")
	public void aHubWithAnAvailableBike(String hubName, String code, int batteryPct) {
		jdbc.update("""
			insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
			select ?, name, 'HOTEL', id, 40.6405, -8.6538, 10 from hotel where name = ?
			on conflict (name) do nothing
			""", UUID.randomUUID(), hubName);
		var hubId = jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
		jdbc.update("""
			insert into vehicle (id, code, type, status, hub_id, battery_pct)
			values (?, ?, 'E_BIKE', 'AVAILABLE', ?, ?)
			""", UUID.randomUUID(), code, hubId, batteryPct);
	}

	// Published over MQTT as the simulator does; the step returns once the platform has stored the
	// reading, so the next step sees it.
	@When("telemetry reports that bike {string} has {int} % battery")
	public void telemetryReportsBattery(String code, int batteryPct) {
		var hubId = jdbc.queryForObject("select hub_id from vehicle where code = ?", UUID.class, code);
		TestMosquitto.publishTelemetry(code, batteryPct, Objects.toString(hubId, null));
		await().atMost(TELEMETRY_LAG).until(() -> Integer.valueOf(batteryPct).equals(
			jdbc.queryForObject("select battery_pct from vehicle where code = ?", Integer.class, code)));
	}

	@When("the guest lists the bikes at hub {string}")
	public void theGuestListsTheBikesAtHub(String hubName) {
		var hubId = jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
		context.setLastResponse(given()
			.auth().oauth2(context.accessToken())
			.when()
			.get("/api/v1/hubs/{hubId}/vehicles", hubId));
	}

	@Then("bike {string} is not bookable")
	public void bikeIsNotBookable(String code) {
		var response = context.lastResponse();
		assertThat(response.statusCode()).isEqualTo(200);
		var bookable = response.jsonPath().getList("findAll { it.code == '" + code + "' }.bookable", Boolean.class);
		assertThat(bookable).containsExactly(false);
	}

}
