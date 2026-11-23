package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-50 (register a stay).
public class StaySteps {

	private static final String STAY_CODE_FORMAT = "^[A-HJ-NP-Z2-9]{8}$";

	private final ScenarioContext context;
	private final KeycloakTestUsers users;
	private final JdbcTemplate jdbc;

	public StaySteps(ScenarioContext context, KeycloakTestUsers users, JdbcTemplate jdbc) {
		this.context = context;
		this.users = users;
		this.jdbc = jdbc;
	}

	// Scenarios share one database, so each starts without stays. Last of the cleanups (see
	// HubSteps), since guests reference stays.
	@Before(order = 10000)
	public void removeStays() {
		jdbc.update("delete from stay");
	}

	private String hotelId(String hotelName) {
		return jdbc.queryForObject("select id::text from hotel where name = ?", String.class, hotelName);
	}

	@Given("the hotel manager of {string} is logged in")
	public void theHotelManagerOfIsLoggedIn(String hotelName) {
		context.setAccessToken(users.logIn("Gestor " + hotelName, "HOTEL_MANAGER", hotelId(hotelName)));
	}

	@Given("a guest {string} is logged in")
	public void aGuestIsLoggedIn(String fullName) {
		context.setAccessToken(users.logIn(fullName, "GUEST", null));
	}

	@When("they register a stay at {string} for guest {string} from {string} to {string}")
	public void theyRegisterAStay(String hotelName, String guestName, String checkIn, String checkOut) {
		var body = Map.of(
			"hotelId", hotelId(hotelName),
			"guestName", guestName,
			"checkIn", checkIn,
			"checkOut", checkOut);
		context.setLastResponse(given()
			.auth().oauth2(context.accessToken())
			.contentType(ContentType.JSON)
			.body(body)
			.when()
			.post("/api/v1/stays"));
	}

	@Then("the stay is registered with check-in {string} and check-out {string}")
	public void theStayIsRegisteredWith(String checkIn, String checkOut) {
		var response = context.lastResponse().jsonPath();
		assertThat(response.getString("checkIn")).isEqualTo(checkIn);
		assertThat(response.getString("checkOut")).isEqualTo(checkOut);
		var stored = jdbc.queryForMap(
			"select check_in::text as check_in, check_out::text as check_out from stay where id = ?::uuid",
			response.getString("id"));
		assertThat(stored).containsEntry("check_in", checkIn).containsEntry("check_out", checkOut);
	}

	@Then("the response contains a stay code")
	public void theResponseContainsAStayCode() {
		assertThat(context.lastResponse().jsonPath().getString("stayCode")).matches(STAY_CODE_FORMAT);
	}

	@Then("the stay code is different from every other registered stay code")
	public void theStayCodeIsUnique() {
		var code = context.lastResponse().jsonPath().getString("stayCode");
		var stays = jdbc.queryForObject("select count(*) from stay where stay_code = ?", Integer.class, code);
		assertThat(stays).isEqualTo(1);
	}

	@Then("no stay is registered for guest {string}")
	public void noStayIsRegisteredForGuest(String guestName) {
		var stays = jdbc.queryForObject("select count(*) from stay where guest_name = ?", Integer.class, guestName);
		assertThat(stays).isZero();
	}

}
