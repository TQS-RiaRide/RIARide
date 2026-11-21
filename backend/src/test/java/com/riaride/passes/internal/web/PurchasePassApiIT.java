package com.riaride.passes.internal.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.payments.PaymentLedger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// Integration test (Failsafe): POST /api/v1/passes through the real security filter chain, with
// PostgreSQL in Testcontainers and the Stripe API stubbed by WireMock (ADR 0008). Only the
// JwtDecoder is replaced, so the tests choose the caller's role and subject. Today is fixed at
// 30 November 2026 in Vila Ria.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class PurchasePassApiIT {

	private static final String PASSES = "/api/v1/passes";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MARES_SALINAS = "00000000-0000-0000-0000-000000000003";
	private static final String GUEST_TOKEN = "guest-token";
	private static final String ENDED_GUEST_TOKEN = "ended-guest-token";
	private static final String OPERATOR_TOKEN = "operator-token";
	private static final String GUEST_SUBJECT = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
	private static final String ENDED_GUEST_SUBJECT = "7b2f8a4c-5d3e-4f9a-8b1c-2d3e4f5a6b71";

	@RegisterExtension
	static WireMockExtension stripe = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	@DynamicPropertySource
	static void stripeProperties(DynamicPropertyRegistry registry) {
		registry.add("riaride.payments.stripe.api-base", stripe::baseUrl);
		registry.add("riaride.payments.stripe.read-timeout", () -> "500ms");
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoSpyBean
	private PaymentLedger ledger;

	@BeforeEach
	void setUp() {
		when(jwtDecoder.decode(GUEST_TOKEN)).thenReturn(jwt(GUEST_TOKEN, GUEST_SUBJECT, "GUEST"));
		when(jwtDecoder.decode(ENDED_GUEST_TOKEN)).thenReturn(jwt(ENDED_GUEST_TOKEN, ENDED_GUEST_SUBJECT, "GUEST"));
		when(jwtDecoder.decode(OPERATOR_TOKEN)).thenReturn(jwt(OPERATOR_TOKEN, UUID.randomUUID().toString(), "OPERATOR"));
		givenGuest(GUEST_SUBJECT, "rita.almeida@example.test", "2026-11-28", "2026-12-02");
		givenGuest(ENDED_GUEST_SUBJECT, "tomas.ferreira@example.test", "2026-11-25", "2026-11-29");
		jdbc.update("update pass_price set price_cents = 2500, deposit_cents = 5000 where pass_type = 'STAY'");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from payment");
		jdbc.update("delete from pass");
		jdbc.update("delete from guest");
		jdbc.update("delete from stay");
	}

	private static Jwt jwt(String token, String subject, String role) {
		return Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject(subject)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("realm_access", Map.of("roles", List.of(role)))
			.build();
	}

	private static int code = 0;

	private void givenGuest(String keycloakId, String email, String checkIn, String checkOut) {
		var stayId = UUID.randomUUID().toString();
		var stayCode = "PASS" + "ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(code++ % 24) + "234";
		jdbc.update("""
			insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
			values (?::uuid, ?::uuid, 'Rita Almeida', ?, ?::date, ?::date, 'ACTIVE')
			""", stayId, MARES_SALINAS, stayCode, checkIn, checkOut);
		jdbc.update("""
			insert into guest (id, keycloak_id, stay_id, email, locale)
			values (?::uuid, ?, ?::uuid, ?, 'pt')
			""", UUID.randomUUID().toString(), keycloakId, stayId, email);
	}

	private void givenStripeAcceptsTheCard() {
		stripe.stubFor(post("/v1/customers").willReturn(okJson("{\"id\": \"cus_rita\", \"object\": \"customer\"}")));
		stripe.stubFor(post("/v1/payment_intents").willReturn(okJson("""
			{"id": "pi_purchase", "object": "payment_intent", "status": "succeeded", "amount": 7500,
			 "currency": "eur", "customer": "cus_rita", "payment_method": "pm_saved_card"}
			""")));
	}

	private void givenStripeDeclinesTheCard() {
		stripe.stubFor(post("/v1/customers").willReturn(okJson("{\"id\": \"cus_rita\", \"object\": \"customer\"}")));
		stripe.stubFor(post("/v1/payment_intents").willReturn(aResponse().withStatus(402)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"type\": \"card_error\", \"code\": \"card_declined\", \"message\": \"Your card was declined.\"}}")));
	}

	private static String body(String passType) {
		return """
			{"passType": "%s", "paymentMethod": "pm_card_visa"}
			""".formatted(passType);
	}

	private ResultActions buy(String token, String json, String idempotencyKey) throws Exception {
		var request = MockMvcRequestBuilders.post(PASSES).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (idempotencyKey != null) {
			request.header("Idempotency-Key", idempotencyKey);
		}
		return mvc.perform(request);
	}

	private List<String> referencesSentToStripe() {
		return stripe.findAll(postRequestedFor(urlPathEqualTo("/v1/payment_intents"))).stream()
			.map(request -> request.getBodyAsString().replaceAll(".*metadata(?:\\[|%5B)reference(?:\\]|%5D)=([^&]+).*", "$1"))
			.toList();
	}

	private int rows(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	@Requirement("RIA-52")
	@Test
	void shouldChargePriceAndDepositAndCreateAnInactiveStayPass() throws Exception {
		givenStripeAcceptsTheCard();

		var result = buy(GUEST_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/v1/passes/[0-9a-f-]{36}$")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.passType").value("STAY"))
			.andExpect(jsonPath("$.status").value("INACTIVE"))
			.andExpect(jsonPath("$.priceCents").value(2500))
			.andExpect(jsonPath("$.depositCents").value(5000))
			.andExpect(jsonPath("$.currency").value("EUR"))
			.andReturn();

		var id = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id");
		stripe.verify(1, postRequestedFor(urlPathEqualTo("/v1/payment_intents"))
			.withHeader("Idempotency-Key", matching("[0-9a-f-]{36}:purchase-1"))
			.withRequestBody(containing("amount=7500"))
			.withRequestBody(containing("currency=eur"))
			.withRequestBody(containing("pass-" + id)));
		var pass = jdbc.queryForMap("select pass_type, status, price_cents, deposit_cents, deposit_status, activated_at from pass where id = ?::uuid", id);
		assertThat(pass).containsEntry("pass_type", "STAY").containsEntry("status", "INACTIVE")
			.containsEntry("price_cents", 2500).containsEntry("deposit_cents", 5000)
			.containsEntry("deposit_status", "HELD").containsEntry("activated_at", null);
		var payment = jdbc.queryForMap("select kind, amount_cents, status, provider_ref, idempotency_key, customer_ref, payment_method_ref from payment where pass_id = ?::uuid", id);
		assertThat(payment).containsEntry("kind", "PURCHASE").containsEntry("amount_cents", 7500L)
			.containsEntry("status", "SUCCEEDED").containsEntry("provider_ref", "pi_purchase")
			.containsEntry("customer_ref", "cus_rita")
			.containsEntry("payment_method_ref", "pm_saved_card");
		// The key is stored scoped by guest, so another guest's key can never match it.
		assertThat((String) payment.get("idempotency_key")).matches("[0-9a-f-]{36}:purchase-1");
	}

	// Seeded provisional base prices (Product Owner decision, until RIA-121): 1-day 10 EUR, 3-day 25 EUR, deposit 50 EUR.
	@Requirement("RIA-52")
	@Test
	void shouldBuyAOneDayPassAtItsSeededBasePrice() throws Exception {
		givenStripeAcceptsTheCard();

		buy(GUEST_TOKEN, body("ONE_DAY"), "purchase-1")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.passType").value("ONE_DAY"))
			.andExpect(jsonPath("$.priceCents").value(1000))
			.andExpect(jsonPath("$.depositCents").value(5000));

		stripe.verify(postRequestedFor(urlPathEqualTo("/v1/payment_intents")).withRequestBody(containing("amount=6000")));
	}

	@Requirement("RIA-52")
	@Test
	void shouldBuyAThreeDayPassAtItsSeededBasePrice() throws Exception {
		givenStripeAcceptsTheCard();

		buy(GUEST_TOKEN, body("THREE_DAY"), "purchase-1")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.priceCents").value(2500))
			.andExpect(jsonPath("$.depositCents").value(5000));
	}

	@Requirement("RIA-52")
	@Test
	void shouldCreateNoPassWhenTheCardIsDeclined() throws Exception {
		givenStripeDeclinesTheCard();

		buy(GUEST_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isPaymentRequired())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Payment declined"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Stripe"))));

		assertThat(rows("pass")).isZero();
		assertThat(rows("payment")).isZero();
	}

	@Requirement("RIA-52")
	@Test
	void shouldCreateNoPassWhenThePaymentSandboxTimesOut() throws Exception {
		stripe.stubFor(post("/v1/customers").willReturn(okJson("{\"id\": \"cus_rita\", \"object\": \"customer\"}")));
		stripe.stubFor(post("/v1/payment_intents").willReturn(okJson("{}").withFixedDelay(2_000)));

		buy(GUEST_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(rows("pass")).isZero();
		assertThat(rows("payment")).isZero();
	}

	@Requirement("RIA-52")
	@Test
	void shouldNeverChargeTwiceForTheSameIdempotencyKey() throws Exception {
		givenStripeAcceptsTheCard();
		var first = buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		var second = buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<String>read(second, "$.id")).isEqualTo(JsonPath.<String>read(first, "$.id"));
		stripe.verify(1, postRequestedFor(urlPathEqualTo("/v1/payment_intents")));
		assertThat(rows("pass")).isEqualTo(1);
		assertThat(rows("payment")).isEqualTo(1);
	}

	// Product Owner decision: one open pass per guest.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseASecondPassWhileOneIsOpen() throws Exception {
		givenStripeAcceptsTheCard();
		buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated());

		buy(GUEST_TOKEN, body("ONE_DAY"), "purchase-2")
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Pass already open"));

		stripe.verify(1, postRequestedFor(urlPathEqualTo("/v1/payment_intents")));
		assertThat(rows("pass")).isEqualTo(1);
	}

	// BR1: only guests with an active stay can buy a pass.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseAGuestWhoseStayHasEnded() throws Exception {
		givenStripeAcceptsTheCard();

		buy(ENDED_GUEST_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("No active stay"));

		stripe.verify(0, postRequestedFor(urlPathEqualTo("/v1/payment_intents")));
		assertThat(rows("pass")).isZero();
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseAUserWhoIsNotAGuest() throws Exception {
		buy(OPERATOR_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(rows("pass")).isZero();
	}

	@Requirement("RIA-52")
	@Test
	void shouldRequireAToken() throws Exception {
		buy(null, body("STAY"), "purchase-1")
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-52")
	@Test
	void shouldRequireAnIdempotencyKey() throws Exception {
		buy(GUEST_TOKEN, body("STAY"), null)
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(rows("pass")).isZero();
	}

	// Two purchases at once with different keys (a double click): one pass, one charge.
	@Requirement("RIA-52")
	@Test
	void shouldChargeOnceWhenTwoPurchasesWithDifferentKeysArriveAtOnce() throws Exception {
		givenStripeAcceptsTheCard();
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		var statuses = new ArrayList<Integer>();
		try {
			var results = new ArrayList<Future<Integer>>();
			for (var key : List.of("purchase-a", "purchase-b")) {
				results.add(executor.submit(() -> {
					start.await();
					return buy(GUEST_TOKEN, body("STAY"), key).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (var result : results) {
				statuses.add(result.get());
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		stripe.verify(1, postRequestedFor(urlPathEqualTo("/v1/payment_intents")));
		assertThat(rows("pass")).isEqualTo(1);
		assertThat(rows("payment")).isEqualTo(1);
	}

	// The card was charged but the pass could not be stored: the charge is refunded.
	@Requirement("RIA-52")
	@Test
	void shouldRefundTheChargeWhenThePassCannotBeStored() throws Exception {
		givenStripeAcceptsTheCard();
		stripe.stubFor(post("/v1/refunds").willReturn(okJson("""
			{"id": "re_purchase", "object": "refund", "status": "succeeded", "amount": 7500, "payment_intent": "pi_purchase"}
			""")));
		doThrow(new IllegalStateException("simulated failure")).when(ledger).recordPurchase(any(), any(), any(), any());

		buy(GUEST_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Pass not created"));

		stripe.verify(1, postRequestedFor(urlPathEqualTo("/v1/refunds"))
			.withRequestBody(containing("payment_intent=pi_purchase"))
			.withRequestBody(containing("amount=7500")));
		assertThat(rows("pass")).isZero();
		assertThat(rows("payment")).isZero();
	}

	// After a timeout, the client retries with the same key as the 503 says: Stripe receives the same
	// parameters (the pass id comes from the key), so its cached result applies, and one pass is created.
	@Requirement("RIA-52")
	@Test
	void shouldCreateThePassWhenTheClientRetriesWithTheSameKeyAfterATimeout() throws Exception {
		stripe.stubFor(post("/v1/customers").willReturn(okJson("{\"id\": \"cus_rita\", \"object\": \"customer\"}")));
		stripe.stubFor(post("/v1/payment_intents").willReturn(okJson("{}").withFixedDelay(2_000)));
		buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isServiceUnavailable());
		var firstReference = referencesSentToStripe().getFirst();
		stripe.resetAll();
		givenStripeAcceptsTheCard();

		buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated());

		assertThat(rows("pass")).isEqualTo(1);
		var id = jdbc.queryForObject("select id::text from pass", String.class);
		assertThat(referencesSentToStripe()).containsExactly("pass-" + id);
		assertThat(firstReference).isEqualTo("pass-" + id);
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseTheSameKeyForADifferentPassType() throws Exception {
		givenStripeAcceptsTheCard();
		buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated());

		buy(GUEST_TOKEN, body("ONE_DAY"), "purchase-1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Idempotency key already used"));

		stripe.verify(1, postRequestedFor(urlPathEqualTo("/v1/payment_intents")));
	}

	// A guest whose stay ended after a successful purchase still gets that purchase back on replay.
	@Requirement("RIA-52")
	@Test
	void shouldReplayAPurchaseEvenAfterTheStayHasEnded() throws Exception {
		givenStripeAcceptsTheCard();
		var first = buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		jdbc.update("update stay set check_out = date '2026-11-29', check_in = date '2026-11-25' where id = (select stay_id from guest where keycloak_id = ?)", GUEST_SUBJECT);

		var replay = buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<String>read(replay, "$.id")).isEqualTo(JsonPath.<String>read(first, "$.id"));
	}

	// BR1 boundaries, with today fixed at 30 November 2026.
	@Requirement("RIA-52")
	@Test
	void shouldSellAPassOnTheCheckInDay() throws Exception {
		givenStripeAcceptsTheCard();
		jdbc.update("update stay set check_in = date '2026-11-30', check_out = date '2026-12-03' where id = (select stay_id from guest where keycloak_id = ?)", GUEST_SUBJECT);

		buy(GUEST_TOKEN, body("STAY"), "purchase-1").andExpect(status().isCreated());
	}

	@Requirement("RIA-52")
	@Test
	void shouldSellAPassOnTheCheckOutDay() throws Exception {
		givenStripeAcceptsTheCard();
		jdbc.update("update stay set check_in = date '2026-11-27', check_out = date '2026-11-30' where id = (select stay_id from guest where keycloak_id = ?)", GUEST_SUBJECT);

		buy(GUEST_TOKEN, body("ONE_DAY"), "purchase-1").andExpect(status().isCreated());
	}

	@Requirement("RIA-52")
	@Test
	void shouldTellTheGuestToUseANewKeyAfterADeclinedCard() throws Exception {
		givenStripeDeclinesTheCard();

		buy(GUEST_TOKEN, body("STAY"), "purchase-1")
			.andExpect(status().isPaymentRequired())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("new Idempotency-Key")));
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseAnUnknownPassType() throws Exception {
		buy(GUEST_TOKEN, body("WEEK"), "purchase-1")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(rows("pass")).isZero();
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseAPurchaseWithoutPaymentMethod() throws Exception {
		buy(GUEST_TOKEN, "{\"passType\": \"STAY\"}", "purchase-1")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

}
