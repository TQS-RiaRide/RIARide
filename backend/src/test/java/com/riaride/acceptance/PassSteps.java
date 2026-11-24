package com.riaride.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-52 (buy a pass), and the logged-in guest with an active stay that later stories reuse.
public class PassSteps {

	private static final String MARES_SALINAS_STAY_CODE_PREFIX = "PASS";

	private final ScenarioContext context;
	private final KeycloakTestUsers users;
	private final JdbcTemplate jdbc;

	private String guestId;

	public PassSteps(ScenarioContext context, KeycloakTestUsers users, JdbcTemplate jdbc) {
		this.context = context;
		this.users = users;
		this.jdbc = jdbc;
	}

	// Before the guest (9000) and stay (10000) cleanups: payments and passes reference guests.
	@Before(order = 8000)
	public void deletePassesAndResetStripe() {
		jdbc.update("delete from payment");
		jdbc.update("delete from pass");
		CucumberSpringConfiguration.STRIPE.resetAll();
	}

	private static int cents(String euros) {
		return new BigDecimal(euros).movePointRight(2).intValueExact();
	}

	@Given("the base price of a stay pass is {word} EUR and the deposit is {word} EUR")
	public void theBasePriceOfAStayPassIs(String price, String deposit) {
		jdbc.update("update pass_price set price_cents = ?, deposit_cents = ? where pass_type = 'STAY'",
			cents(price), cents(deposit));
	}

	// A stay that is active on the fixed test date (30 November 2026), a guest account linked to it,
	// and a real Keycloak token for that guest.
	@Given("a guest {string} with an active stay at {string} is logged in")
	public void aGuestWithAnActiveStayIsLoggedIn(String fullName, String hotelName) {
		var token = users.logIn(fullName, "GUEST", null);
		var keycloakId = subjectOf(token);
		var stayId = UUID.randomUUID().toString();
		jdbc.update("""
			insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
			select ?::uuid, id, ?, ?, date '2026-11-28', date '2026-12-02', 'ACTIVE' from hotel where name = ?
			""", stayId, fullName, MARES_SALINAS_STAY_CODE_PREFIX + randomCodeSuffix(), hotelName);
		guestId = UUID.randomUUID().toString();
		jdbc.update("""
			insert into guest (id, keycloak_id, stay_id, email, locale) values (?::uuid, ?, ?::uuid, ?, 'pt')
			""", guestId, keycloakId, stayId, keycloakId + "@example.test");
		context.setAccessToken(token);
	}

	private static String randomCodeSuffix() {
		var alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		var random = new java.security.SecureRandom();
		var suffix = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			suffix.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return suffix.toString();
	}

	private static String subjectOf(String token) {
		var payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
		var start = payload.indexOf("\"sub\":\"") + 7;
		return payload.substring(start, payload.indexOf('"', start));
	}

	// A paid pass that is open and not yet active, as RIA-52 leaves it after a purchase. Used by the
	// stories that need a guest with a pass (RIA-59), without going through the payment sandbox.
	@Given("the guest has a valid {string} pass")
	public void theGuestHasAValidPass(String passType) {
		jdbc.update("""
			insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
			select ?::uuid, ?::uuid, pass_type, 'INACTIVE', price_cents, deposit_cents, 'HELD'
			from pass_price where pass_type = ?
			""", UUID.randomUUID().toString(), guestId, passType.toUpperCase(Locale.ROOT));
	}

	@Given("the payment sandbox accepts the card")
	public void thePaymentSandboxAcceptsTheCard() {
		var stripe = CucumberSpringConfiguration.STRIPE;
		stripe.stubFor(post("/v1/customers").willReturn(okJson("{\"id\": \"cus_guest\", \"object\": \"customer\"}")));
		stripe.stubFor(post("/v1/payment_intents").willReturn(okJson("""
			{"id": "pi_purchase", "object": "payment_intent", "status": "succeeded",
			 "currency": "eur", "customer": "cus_guest", "payment_method": "pm_saved_card"}
			""")));
	}

	@Given("the payment sandbox declines the card")
	public void thePaymentSandboxDeclinesTheCard() {
		var stripe = CucumberSpringConfiguration.STRIPE;
		stripe.stubFor(post("/v1/customers").willReturn(okJson("{\"id\": \"cus_guest\", \"object\": \"customer\"}")));
		stripe.stubFor(post("/v1/payment_intents").willReturn(aResponse().withStatus(402)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"card_error\", \"code\": \"card_declined\", \"message\": \"Your card was declined.\"}}")));
	}

	@When("the guest buys a {string} pass")
	public void theGuestBuysAPass(String passType) {
		context.setLastResponse(given()
			.auth().oauth2(context.accessToken())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(ContentType.JSON)
			.body(Map.of("passType", passType.toUpperCase(Locale.ROOT), "paymentMethod", "pm_card_visa"))
			.when()
			.post("/api/v1/passes"));
	}

	// The price and the deposit are charged together, in one payment of their sum.
	@Then("the payment sandbox charged {word} EUR for the pass")
	public void thePaymentSandboxChargedForThePass(String price) {
		var pass = jdbc.queryForMap("select price_cents, deposit_cents from pass where guest_id = ?::uuid", guestId);
		assertThat(pass.get("price_cents")).isEqualTo(cents(price));
		var total = cents(price) + (Integer) pass.get("deposit_cents");
		CucumberSpringConfiguration.STRIPE.verify(1, postRequestedFor(urlPathEqualTo("/v1/payment_intents"))
			.withRequestBody(containing("amount=" + total)));
	}

	@Then("the payment sandbox charged a deposit of {word} EUR")
	public void thePaymentSandboxChargedADepositOf(String deposit) {
		var depositCents = jdbc.queryForObject("select deposit_cents from pass where guest_id = ?::uuid", Integer.class, guestId);
		assertThat(depositCents).isEqualTo(cents(deposit));
		var charged = jdbc.queryForObject("select amount_cents from payment p join pass s on s.id = p.pass_id where s.guest_id = ?::uuid and p.status = 'SUCCEEDED'", Integer.class, guestId);
		assertThat(charged).isGreaterThanOrEqualTo(cents(deposit));
	}

	@Then("the guest has a {string} pass that is not active yet")
	public void theGuestHasAPassThatIsNotActiveYet(String passType) {
		var pass = jdbc.queryForMap("select pass_type, status, activated_at from pass where guest_id = ?::uuid", guestId);
		assertThat(pass.get("pass_type")).isEqualTo(passType.toUpperCase(Locale.ROOT));
		assertThat(pass.get("status")).isEqualTo("INACTIVE");
		assertThat(pass.get("activated_at")).isNull();
	}

	@Then("the guest has no pass")
	public void theGuestHasNoPass() {
		var passes = jdbc.queryForObject("select count(*) from pass where guest_id = ?::uuid", Integer.class, guestId);
		assertThat(passes).isZero();
	}

}
