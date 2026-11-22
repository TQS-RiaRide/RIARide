package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.riaride.TestKeycloak;
import com.riaride.accounts.internal.domain.StayCode;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-51 (guest sign-up with a stay code).
public class GuestSignUpSteps {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ScenarioContext context;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	private String stayId;
	private String stayCode;

	public GuestSignUpSteps(ScenarioContext context, JdbcTemplate jdbc, Clock clock) {
		this.context = context;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// Before the stay steps' cleanup (order 10000): guests reference stays.
	@Before(order = 9000)
	public void deleteGuests() {
		for (var email : jdbc.queryForList("select email from guest", String.class)) {
			TestKeycloak.deleteUsersWithEmail(email);
		}
		jdbc.update("delete from guest");
	}

	private static String newStayCode() {
		var code = new StringBuilder();
		for (int i = 0; i < StayCode.LENGTH; i++) {
			code.append(StayCode.ALPHABET.charAt(RANDOM.nextInt(StayCode.ALPHABET.length())));
		}
		return code.toString();
	}

	// Inserted directly: a stay that has already ended cannot be registered through the API.
	@Given("a stay at {string} for guest {string} from {string} to {string}")
	public void aStayAtForGuestFromTo(String hotelName, String guestName, String checkIn, String checkOut) {
		stayId = UUID.randomUUID().toString();
		stayCode = newStayCode();
		jdbc.update("""
			insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
			select ?::uuid, id, ?, ?, ?::date, ?::date, 'ACTIVE' from hotel where name = ?
			""", stayId, guestName, stayCode, checkIn, checkOut, hotelName);
	}

	// The clock is fixed for the whole test run; this step states the date the scenario relies on.
	@Given("today is {string}")
	public void todayIs(String date) {
		assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.parse(date));
	}

	@When("a guest signs up with email {string}, password {string} and that stay code")
	public void aGuestSignsUpWithThatStayCode(String email, String password) {
		context.setLastResponse(given()
			.contentType(ContentType.JSON)
			.body(Map.of("email", email, "password", password, "stayCode", stayCode))
			.when()
			.post("/api/v1/guests"));
	}

	@Then("an account exists for {string} linked to that stay")
	public void anAccountExistsLinkedToThatStay(String email) {
		var linkedStay = jdbc.queryForObject("select stay_id::text from guest where email = ?", String.class, email);
		assertThat(linkedStay).isEqualTo(stayId);
		assertThat(TestKeycloak.usersWithEmail(email)).hasSize(1);
	}

	@Then("the problem detail says the stay has already ended")
	public void theProblemDetailSaysTheStayHasAlreadyEnded() {
		var problem = context.lastResponse().jsonPath();
		assertThat(problem.getString("title")).isEqualTo("Stay already ended");
		assertThat(problem.getString("detail")).contains("already ended");
	}

	@Then("no account exists for {string}")
	public void noAccountExistsFor(String email) {
		var guests = jdbc.queryForObject("select count(*) from guest where email = ?", Integer.class, email);
		assertThat(guests).isZero();
		assertThat(TestKeycloak.usersWithEmail(email)).isEmpty();
	}

}
