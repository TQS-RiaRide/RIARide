package com.riaride.trips.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import com.riaride.telemetry.VehicleCommands;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// Integration test (Failsafe): POST /api/v1/trips and GET /api/v1/trips/{id} through the real
// security filter chain, with PostgreSQL in Testcontainers (RIA-54). Only the JwtDecoder and the
// MQTT commands are replaced, so the tests choose the caller and see the UNLOCK sent after commit.
// Concurrent starts are settled by the database: one pass gets one trip in progress (BR6), and one
// bike goes to one guest. Today is fixed at 30 November 2026, 10:00 in Vila Ria.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class StartTripApiIT {

	private static final String TRIPS = "/api/v1/trips";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
	private static final String RITA = "rita-token";
	private static final String TOMAS = "tomas-token";
	private static final String NO_PASS = "no-pass-token";
	private static final String OPERATOR = "operator-token";
	private static final String RITA_SUBJECT = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
	private static final String TOMAS_SUBJECT = "7b2f8a4c-5d3e-4f9a-8b1c-2d3e4f5a6b71";
	private static final String NO_PASS_SUBJECT = "8c3a9b5d-6e4f-4a0b-9c2d-3e4f5a6b7c82";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private VehicleCommands vehicleCommands;

	@BeforeEach
	void guestsAndBikes() {
		when(jwtDecoder.decode(RITA)).thenReturn(jwt(RITA, RITA_SUBJECT, "GUEST"));
		when(jwtDecoder.decode(TOMAS)).thenReturn(jwt(TOMAS, TOMAS_SUBJECT, "GUEST"));
		when(jwtDecoder.decode(NO_PASS)).thenReturn(jwt(NO_PASS, NO_PASS_SUBJECT, "GUEST"));
		when(jwtDecoder.decode(OPERATOR)).thenReturn(jwt(OPERATOR, UUID.randomUUID().toString(), "OPERATOR"));
		givenPass(givenGuest(RITA_SUBJECT, "TRPA2345", "rita.almeida@example.test"));
		givenPass(givenGuest(TOMAS_SUBJECT, "TRPB2345", "tomas.ferreira@example.test"));
		givenGuest(NO_PASS_SUBJECT, "TRPC2345", "ines.costa@example.test");
		var hubId = UUID.randomUUID();
		jdbc.update("""
			insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
			values (?, 'Marés Centro', 'HOTEL', ?::uuid, 40.6405, -8.6538, 10)
			""", hubId, MARES_CENTRO);
		givenBike("EB-201", hubId);
		givenBike("EB-202", hubId);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from trip");
		jdbc.update("delete from reservation");
		jdbc.update("delete from vehicle");
		jdbc.update("delete from hub");
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

	// A stay that is active on the fixed test date, and the guest account linked to it.
	private UUID givenGuest(String keycloakId, String stayCode, String email) {
		var stayId = UUID.randomUUID();
		jdbc.update("""
			insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
			values (?, ?::uuid, 'Rita Almeida', ?, date '2026-11-28', date '2026-12-02', 'ACTIVE')
			""", stayId, MARES_CENTRO, stayCode);
		var guestId = UUID.randomUUID();
		jdbc.update("""
			insert into guest (id, keycloak_id, stay_id, email, locale) values (?, ?, ?, ?, 'pt')
			""", guestId, keycloakId, stayId, email);
		return guestId;
	}

	// A paid stay pass, not active until the first trip (BR4).
	private void givenPass(UUID guestId) {
		jdbc.update("""
			insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
			values (?, ?, 'STAY', 'INACTIVE', 4000, 5000, 'HELD')
			""", UUID.randomUUID(), guestId);
	}

	private void givenBike(String code, UUID hubId) {
		jdbc.update("""
			insert into vehicle (id, code, type, status, hub_id, battery_pct)
			values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80)
			""", UUID.randomUUID(), code, hubId);
	}

	private ResultActions start(String token, String vehicleCode) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.post(TRIPS)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"vehicleCode\": \"" + vehicleCode + "\"}"));
	}

	private ResultActions read(String token, String location) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.get(location).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String bikeStatus(String code) {
		return jdbc.queryForObject("select status from vehicle where code = ?", String.class, code);
	}

	private int tripsInProgress() {
		return jdbc.queryForObject("select count(*) from trip where status in ('STARTING', 'IN_USE')", Integer.class);
	}

	@Requirement("RIA-54")
	@Test
	void shouldAcceptStartAndReturnStartingTrip() throws Exception {
		var response = start(RITA, "EB-201")
			.andExpect(status().isAccepted())
			.andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("http://localhost/api/v1/trips/[0-9a-f-]{36}")))
			.andExpect(jsonPath("$.status").value("STARTING"))
			.andExpect(jsonPath("$.vehicleCode").value("EB-201"))
			.andExpect(jsonPath("$.requestedAt").value("2026-11-30T10:00:00Z"))
			.andExpect(jsonPath("$.startedAt").doesNotExist())
			.andReturn().getResponse();

		assertThat(bikeStatus("EB-201")).isEqualTo("RESERVED");
		var reservation = jdbc.queryForMap("select status, expires_at from reservation");
		assertThat(reservation.get("status")).isEqualTo("ACTIVE");
		assertThat(((java.sql.Timestamp) reservation.get("expires_at")).toInstant())
			.isEqualTo(Instant.parse("2026-11-30T10:15:00Z"));
		verify(vehicleCommands).unlock("EB-201");
		String id = JsonPath.read(response.getContentAsString(), "$.id");
		assertThat(response.getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/trips/" + id);
	}

	@Requirement("RIA-54")
	@Test
	void shouldReadTheGuestsOwnTrip() throws Exception {
		var location = start(RITA, "EB-201").andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		read(RITA, location)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("STARTING"))
			.andExpect(jsonPath("$.vehicleCode").value("EB-201"));
	}

	@Requirement("RIA-54")
	@Test
	void shouldRefuseSecondStartWith409ProblemAndLeaveOtherBikeAvailable() throws Exception {
		start(RITA, "EB-201").andExpect(status().isAccepted());

		start(RITA, "EB-202")
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Trip already in progress"));

		assertThat(bikeStatus("EB-202")).isEqualTo("AVAILABLE");
		verify(vehicleCommands, never()).unlock("EB-202");
	}

	// BR6 under concurrency: two starts for the same pass on two bikes at the same moment. The
	// database lets one through; the other is a 409 and leaves its bike available.
	@Requirement("RIA-54")
	@Test
	void onlyOneOfTwoParallelStartsForSamePassSucceeds() throws Exception {
		var statuses = startTogether(RITA, "EB-201", RITA, "EB-202");

		assertThat(statuses).containsExactlyInAnyOrder(202, 409);
		assertThat(tripsInProgress()).isEqualTo(1);
		assertThat(List.of(bikeStatus("EB-201"), bikeStatus("EB-202"))).containsExactlyInAnyOrder("RESERVED",
			"AVAILABLE");
	}

	@Requirement("RIA-54")
	@Test
	void onlyOneOfTwoGuestsGetsTheSameBike() throws Exception {
		var statuses = startTogether(RITA, "EB-201", TOMAS, "EB-201");

		assertThat(statuses).containsExactlyInAnyOrder(202, 409);
		assertThat(tripsInProgress()).isEqualTo(1);
		assertThat(bikeStatus("EB-201")).isEqualTo("RESERVED");
	}

	private List<Integer> startTogether(String firstToken, String firstBike, String secondToken, String secondBike)
		throws Exception {
		var go = new CountDownLatch(1);
		try (var threads = Executors.newFixedThreadPool(2)) {
			var first = threads.submit(() -> {
				go.await();
				return start(firstToken, firstBike).andReturn().getResponse().getStatus();
			});
			var second = threads.submit(() -> {
				go.await();
				return start(secondToken, secondBike).andReturn().getResponse().getStatus();
			});
			go.countDown();
			return List.of(first.get(), second.get());
		}
	}

	// After a failed start the pass still holds its bike (BR11, BR13): the retry on the same bike
	// reuses the reservation, and another bike is refused while it is held.
	@Requirement("RIA-54")
	@Test
	void shouldReuseTheHeldBikeAfterAFailedStartAndRefuseAnother() throws Exception {
		start(RITA, "EB-201").andExpect(status().isAccepted());
		jdbc.update("update trip set status = 'FAILED', failed_at = requested_at + interval '30 seconds'");

		start(RITA, "EB-201").andExpect(status().isAccepted());
		jdbc.update("update trip set status = 'FAILED', failed_at = requested_at + interval '30 seconds'");
		start(RITA, "EB-202")
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Reservation held for another bike"));

		assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isEqualTo(1);
		assertThat(bikeStatus("EB-202")).isEqualTo("AVAILABLE");
	}

	@Requirement("RIA-54")
	@Test
	void shouldRefuseOperatorWith403() throws Exception {
		start(OPERATOR, "EB-201")
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(bikeStatus("EB-201")).isEqualTo("AVAILABLE");
	}

	@Requirement("RIA-54")
	@Test
	void shouldReturn404ProblemForUnknownBike() throws Exception {
		start(RITA, "EB-999")
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Bike not found"));
	}

	@Requirement("RIA-54")
	@Test
	void shouldReturn403ProblemWithoutValidPass() throws Exception {
		start(NO_PASS, "EB-201")
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("No valid pass"));

		assertThat(bikeStatus("EB-201")).isEqualTo("AVAILABLE");
	}

	@Requirement("RIA-54")
	@Test
	void shouldReturn400ProblemForABlankBikeCode() throws Exception {
		start(RITA, " ")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-54")
	@Test
	void shouldNotShowAnotherGuestsTrip() throws Exception {
		var location = start(RITA, "EB-201").andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		read(TOMAS, location)
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Trip not found"));
	}

}
