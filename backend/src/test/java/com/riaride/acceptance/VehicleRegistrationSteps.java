package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-57 (register a bike). The hub and docked-bike steps are reused by RIA-55 (end a
// trip at a hub). Vehicles are removed before and after each scenario by FleetSteps, and hubs by
// HubSteps.
public class VehicleRegistrationSteps {

	private final ScenarioContext context;
	private final JdbcTemplate jdbc;

	public VehicleRegistrationSteps(ScenarioContext context, JdbcTemplate jdbc) {
		this.context = context;
		this.jdbc = jdbc;
	}

	// A hub named after a hotel of the group is at that hotel; any other name is a tourist spot.
	// The docked bikes are available and get codes of their own (DOCK-<n>-<hub>), so the scenario's
	// own codes stay free.
	@Given("a hub {string} with capacity {int} and {int} bikes docked")
	public void aHubWithCapacityAndBikesDocked(String hubName, int capacity, int docked) {
		var hubId = UUID.randomUUID();
		jdbc.update("""
			insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
			select ?, ?, case when h.id is null then 'TOURIST_SPOT' else 'HOTEL' end, h.id, 40.6510, -8.6820, ?
			from (select 1) as one left join hotel h on h.name = ?
			""", hubId, hubName, capacity, hubName);
		for (int i = 1; i <= docked; i++) {
			jdbc.update("""
				insert into vehicle (id, code, type, status, hub_id, battery_pct)
				values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80)
				""", UUID.randomUUID(), "DOCK-" + i + "-" + hubId.toString().substring(0, 8), hubId);
		}
	}

	@When("the operator registers bike {string} at hub {string}")
	public void theOperatorRegistersBikeAtHub(String code, String hubName) {
		context.setLastResponse(given()
			.auth().oauth2(context.accessToken())
			.contentType(ContentType.JSON)
			.body(Map.of("code", code, "type", "E_BIKE"))
			.when()
			.post("/api/v1/hubs/{hubId}/vehicles", hubId(hubName)));
	}

	// The status as the scenario writes it, for example "available" or "in use".
	@Then("bike {string} is {string}")
	public void bikeIs(String code, String status) {
		var stored = jdbc.queryForList("select status from vehicle where code = ?", String.class, code);
		assertThat(stored).containsExactly(status.toUpperCase(Locale.ROOT).replace(' ', '_'));
	}

	@Then("bike {string} is docked at hub {string}")
	public void bikeIsDockedAtHub(String code, String hubName) {
		var hub = jdbc.queryForList("select hub_id from vehicle where code = ?", UUID.class, code);
		assertThat(hub).containsExactly(hubId(hubName));
	}

	@Then("bike {string} does not exist")
	public void bikeDoesNotExist(String code) {
		var vehicles = jdbc.queryForObject("select count(*) from vehicle where code = ?", Integer.class, code);
		assertThat(vehicles).isZero();
	}

	private UUID hubId(String hubName) {
		return jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
	}

}
