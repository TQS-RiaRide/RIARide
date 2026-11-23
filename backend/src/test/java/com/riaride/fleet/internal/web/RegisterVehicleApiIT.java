package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

// Integration test (Failsafe): POST /api/v1/hubs/{hubId}/vehicles (RIA-57) through the real security
// filter chain, with PostgreSQL in Testcontainers and only the JwtDecoder replaced. OPERATOR and
// ADMIN may register vehicles; every other role gets 403. BR10: a hub holds at most its capacity,
// also when two registrations race for its last free slot. Errors are RFC 9457 problem details,
// and a refused vehicle is not stored.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class RegisterVehicleApiIT {

	private static final String PROBLEM_JSON = "application/problem+json";
	private static final UUID SALINAS = UUID.fromString("00000000-0000-0000-0000-00000000a157");
	private static final UUID PRAIA = UUID.fromString("00000000-0000-0000-0000-00000000a158");
	private static final int CAPACITY = 8;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void seedHub() {
		removeVehiclesAndHub();
		jdbc.update("""
			insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
			values (?, 'Salinas – RIA-57 test hub', 'TOURIST_SPOT', null, 40.6510, -8.6820, ?),
			       (?, 'Praia – RIA-57 test hub', 'TOURIST_SPOT', null, 40.6430, -8.7470, ?)
			""", SALINAS, CAPACITY, PRAIA, CAPACITY);
		for (var role : List.of("GUEST", "OPERATOR", "ADMIN", "HOTEL_MANAGER", "TECHNICIAN")) {
			when(jwtDecoder.decode(role)).thenReturn(jwt(role));
		}
	}

	// The hub and vehicle tables are shared with other tests, so nothing is left behind. Every
	// vehicle this class creates is at one of its two hubs, so only those hubs' vehicles go.
	@AfterEach
	void removeVehiclesAndHub() {
		jdbc.update("delete from vehicle where hub_id in (?, ?)", SALINAS, PRAIA);
		jdbc.update("delete from hub where id in (?, ?)", SALINAS, PRAIA);
	}

	private static Jwt jwt(String role) {
		return Jwt.withTokenValue(role)
			.header("alg", "RS256")
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("realm_access", Map.of("roles", List.of(role)))
			.build();
	}

	private ResultActions register(String token, UUID hubId, String json) throws Exception {
		var request = post("/api/v1/hubs/{hubId}/vehicles", hubId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mvc.perform(request);
	}

	private ResultActions registerBike(String token, String code) throws Exception {
		return register(token, SALINAS, bike(code));
	}

	private static String bike(String code) {
		return "{\"code\": \"" + code + "\", \"type\": \"E_BIKE\"}";
	}

	private void dockBikes(int count) {
		for (int i = 1; i <= count; i++) {
			jdbc.update("insert into vehicle (id, code, type, status, hub_id) values (?, ?, 'E_BIKE', 'AVAILABLE', ?)",
				UUID.randomUUID(), "EB-49" + i, SALINAS);
		}
	}

	private int vehiclesCoded(String code) {
		return jdbc.queryForObject("select count(*) from vehicle where upper(code) = upper(?)", Integer.class, code);
	}

	// Sends the registrations at the same time and returns each response's status and title.
	private List<String> registerConcurrently(List<Callable<ResultActions>> registrations) throws Exception {
		var start = new CountDownLatch(1);
		var outcomes = new ArrayList<String>();
		try (var pool = Executors.newFixedThreadPool(registrations.size())) {
			var futures = new ArrayList<Future<String>>();
			for (var registration : registrations) {
				Callable<String> outcome = () -> {
					start.await();
					var response = registration.call().andReturn().getResponse();
					if (response.getStatus() == 201) {
						return "201";
					}
					var title = JsonPath.<String>read(response.getContentAsString(), "$.title");
					return response.getStatus() + " " + title;
				};
				futures.add(pool.submit(outcome));
			}
			start.countDown();
			for (var future : futures) {
				outcomes.add(future.get());
			}
		}
		return outcomes;
	}

	private int vehiclesAtSalinas() {
		return jdbc.queryForObject("select count(*) from vehicle where hub_id = ?", Integer.class, SALINAS);
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(strings = { "OPERATOR", "ADMIN" })
	void shouldRegisterAvailableBikeDockedAtHubWithFreeSlots(String role) throws Exception {
		dockBikes(3);

		registerBike(role, "EB-401")
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/vehicles/")))
			.andExpect(jsonPath("$.code").value("EB-401"))
			.andExpect(jsonPath("$.type").value("E_BIKE"))
			.andExpect(jsonPath("$.status").value("AVAILABLE"))
			.andExpect(jsonPath("$.hubId").value(SALINAS.toString()))
			.andExpect(jsonPath("$.batteryPct").doesNotExist());

		var stored = jdbc.queryForMap("select status, hub_id, battery_pct from vehicle where code = 'EB-401'");
		assertThat(stored).containsEntry("status", "AVAILABLE").containsEntry("hub_id", SALINAS);
		assertThat(stored.get("battery_pct")).isNull();
	}

	@Requirement("RIA-57")
	@Test
	void shouldPointLocationAtTheNewVehicle() throws Exception {
		var result = registerBike("OPERATOR", "EB-401").andExpect(status().isCreated()).andReturn();

		var id = jdbc.queryForObject("select id from vehicle where code = 'EB-401'", UUID.class);
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/vehicles/" + id);
	}

	// BR10 boundary: 7 of 8 slots taken leaves one free slot.
	@Requirement("RIA-57")
	@Test
	void shouldRegisterBikeInTheLastFreeSlot() throws Exception {
		dockBikes(CAPACITY - 1);

		registerBike("OPERATOR", "EB-401").andExpect(status().isCreated());

		assertThat(vehiclesAtSalinas()).isEqualTo(CAPACITY);
	}

	@Requirement("RIA-57")
	@Test
	void shouldRefuseBikeAtFullHub() throws Exception {
		dockBikes(CAPACITY);

		registerBike("OPERATOR", "EB-401")
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Hub full"));

		assertThat(vehiclesCoded("EB-401")).isZero();
	}

	// Two operators register a bike at the same time in the hub's last free slot: the hub row lock
	// makes the second wait and then see the hub full, so exactly one bike is stored and the other
	// is refused by BR10, not by any other conflict.
	@Requirement("RIA-57")
	@Test
	void shouldStoreOnlyOneBikeWhenTwoRegistrationsRaceForTheLastSlot() throws Exception {
		dockBikes(CAPACITY - 1);

		var outcomes = registerConcurrently(List.of(
			() -> registerBike("OPERATOR", "EB-401"),
			() -> registerBike("OPERATOR", "EB-402")));

		assertThat(outcomes).containsExactlyInAnyOrder("201", "409 Hub full");
		assertThat(vehiclesAtSalinas()).isEqualTo(CAPACITY);
	}

	// The same code registered at the same time at two hubs: the hub locks do not serialise them,
	// so the second may pass the code check and be stopped by the unique index instead. Either way
	// it is refused as a code in use, and one vehicle is stored.
	@Requirement("RIA-57")
	@Test
	void shouldStoreOnlyOneBikeWhenTheSameCodeIsRegisteredAtTwoHubsAtOnce() throws Exception {
		var outcomes = registerConcurrently(List.of(
			() -> register("OPERATOR", SALINAS, bike("EB-401")),
			() -> register("OPERATOR", PRAIA, bike("EB-401"))));

		assertThat(outcomes).containsExactlyInAnyOrder("201", "409 Vehicle code already in use");
		assertThat(vehiclesCoded("EB-401")).isEqualTo(1);
	}

	@Requirement("RIA-57")
	@Test
	void shouldRefuseCodeAlreadyUsedByAnotherVehicle() throws Exception {
		registerBike("OPERATOR", "EB-401").andExpect(status().isCreated());

		registerBike("OPERATOR", "EB-401")
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Vehicle code already in use"))
			.andExpect(jsonPath("$.detail").value(not(containsString("EB-401"))));

		assertThat(vehiclesCoded("EB-401")).isEqualTo(1);
	}

	// A code is in use whatever its case.
	@Requirement("RIA-57")
	@Test
	void shouldRefuseCodeThatDiffersOnlyInCase() throws Exception {
		registerBike("OPERATOR", "EB-401").andExpect(status().isCreated());

		registerBike("OPERATOR", "eb-401")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Vehicle code already in use"));

		assertThat(vehiclesCoded("EB-401")).isEqualTo(1);
	}

	@Requirement("RIA-57")
	@Test
	void shouldRefuseUnknownHub() throws Exception {
		register("OPERATOR", UUID.randomUUID(), bike("EB-401"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Hub not found"));

		assertThat(vehiclesCoded("EB-401")).isZero();
	}

	@Requirement("RIA-57")
	@Test
	void shouldRefuseHubIdThatIsNotAUuid() throws Exception {
		mvc.perform(post("/api/v1/hubs/not-a-uuid/vehicles")
				.header(HttpHeaders.AUTHORIZATION, "Bearer OPERATOR")
				.contentType(MediaType.APPLICATION_JSON)
				.content(bike("EB-401")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Invalid hub id"));
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(strings = {
		"{\"code\": \"EB 401\", \"type\": \"E_BIKE\"}",
		"{\"code\": \"\", \"type\": \"E_BIKE\"}",
		"{\"type\": \"E_BIKE\"}" })
	void shouldRejectInvalidCodeNamingTheField(String json) throws Exception {
		register("OPERATOR", SALINAS, json)
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$['invalid-params'][0].name").value("code"));

		assertThat(vehiclesAtSalinas()).isZero();
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(strings = {
		"{\"code\": \"EB-401\"}",
		"{\"code\": \"EB-401\", \"type\": \"SCOOTER\"}",
		"{\"code\": \"EB-401\", \"type\": 0}",
		"{\"code\": \"EB-401\", \"type\": 7}" })
	void shouldRejectMissingOrUnknownTypeNamingTheField(String json) throws Exception {
		register("OPERATOR", SALINAS, json)
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$['invalid-params'][0].name").value("type"))
			.andExpect(jsonPath("$.detail").value(not(containsString("SCOOTER"))));

		assertThat(vehiclesAtSalinas()).isZero();
	}

	@Requirement("RIA-57")
	@Test
	void shouldRejectBodyThatIsNotJson() throws Exception {
		register("OPERATOR", SALINAS, "not json")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(strings = { "GUEST", "HOTEL_MANAGER", "TECHNICIAN" })
	void shouldForbidRolesOtherThanOperatorAndAdmin(String role) throws Exception {
		registerBike(role, "EB-401")
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(vehiclesCoded("EB-401")).isZero();
	}

	// The role rule runs before the body is read, so an invalid body still gets 403.
	@Requirement("RIA-57")
	@Test
	void shouldForbidGuestEvenWithInvalidBody() throws Exception {
		register("GUEST", SALINAS, "not json").andExpect(status().isForbidden());
	}

	@Requirement("RIA-57")
	@Test
	void shouldRequireAToken() throws Exception {
		registerBike(null, "EB-401").andExpect(status().isUnauthorized());

		assertThat(vehiclesCoded("EB-401")).isZero();
	}

}
