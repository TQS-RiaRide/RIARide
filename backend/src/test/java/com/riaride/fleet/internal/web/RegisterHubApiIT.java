package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

// Integration test (Failsafe): POST /api/v1/hubs (RIA-56) through the real security filter chain,
// with PostgreSQL in Testcontainers. Only the JwtDecoder is replaced, so the tests control which
// role the bearer token carries without a running Keycloak. OPERATOR and ADMIN may register hubs;
// every other role gets 403. A name already used by another hub (exact match) gets 409.
// Invalid input is a 400 problem detail that names each invalid field in "invalid-params"
// (RFC 9457, section 3: [{"name": <field>, "reason": <text>}]), and no hub is stored.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class RegisterHubApiIT {

	private static final String HUBS = "/api/v1/hubs";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MARES_PRAIA_ID = "00000000-0000-0000-0000-000000000002";
	private static final String NAME = "Marés Praia – Beach Front";
	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private static final String OPERATOR = "operator-token";
	private static final String GUEST = "guest-token";
	private static final String HOTEL_MANAGER = "hotel-manager-token";
	private static final String ADMIN = "admin-token";
	private static final String TECHNICIAN = "technician-token";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from hub");
		when(jwtDecoder.decode(OPERATOR)).thenReturn(jwt(OPERATOR, "OPERATOR"));
		when(jwtDecoder.decode(GUEST)).thenReturn(jwt(GUEST, "GUEST"));
		when(jwtDecoder.decode(HOTEL_MANAGER)).thenReturn(jwt(HOTEL_MANAGER, "HOTEL_MANAGER"));
		when(jwtDecoder.decode(ADMIN)).thenReturn(jwt(ADMIN, "ADMIN"));
		when(jwtDecoder.decode(TECHNICIAN)).thenReturn(jwt(TECHNICIAN, "TECHNICIAN"));
	}

	private static Jwt jwt(String token, String role) {
		return Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("realm_access", Map.of("roles", List.of(role)))
			.build();
	}

	// The hub of the acceptance scenario, with fields replaced by raw JSON values given as
	// field, value pairs; a null value removes the field.
	private static String hotelHubWith(String... fieldValuePairs) {
		var fields = new LinkedHashMap<String, String>();
		fields.put("name", "\"" + NAME + "\"");
		fields.put("kind", "\"HOTEL\"");
		fields.put("hotel", "\"Marés Praia\"");
		fields.put("latitude", "40.6405");
		fields.put("longitude", "-8.7538");
		fields.put("capacity", "10");
		for (int i = 0; i < fieldValuePairs.length; i += 2) {
			if (fieldValuePairs[i + 1] == null) {
				fields.remove(fieldValuePairs[i]);
			} else {
				fields.put(fieldValuePairs[i], fieldValuePairs[i + 1]);
			}
		}
		var json = new StringJoiner(", ", "{", "}");
		fields.forEach((key, value) -> json.add("\"" + key + "\": " + value));
		return json.toString();
	}

	private static String validHotelHub() {
		return hotelHubWith();
	}

	private ResultActions postHub(String token, String json) throws Exception {
		var request = post(HUBS).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mvc.perform(request);
	}

	private int hubsNamed(String name) {
		return jdbc.queryForObject("select count(*) from hub where name = ?", Integer.class, name);
	}

	private int hubs() {
		return jdbc.queryForObject("select count(*) from hub", Integer.class);
	}

	private void assertRejectedField(String json, String field) throws Exception {
		postHub(OPERATOR, json)
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.title").value("Invalid hub"))
			.andExpect(jsonPath("$['invalid-params'][*].name", hasItem(field)));

		assertThat(hubs()).isZero();
	}

	// Scenario "The operator registers a hub with capacity for 10 bikes".

	@Requirement("RIA-56")
	@Test
	void shouldRegisterHotelHubAndReturnItWhenOperatorRegistersIt() throws Exception {
		var result = postHub(OPERATOR, validHotelHub())
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/v1/hubs/" + UUID_PATTERN + "$")))
			.andExpect(jsonPath("$.id").value(matchesPattern("^" + UUID_PATTERN + "$")))
			.andExpect(jsonPath("$.name").value(NAME))
			.andExpect(jsonPath("$.kind").value("HOTEL"))
			.andExpect(jsonPath("$.hotel").value("Marés Praia"))
			.andExpect(jsonPath("$.latitude").value(40.6405))
			.andExpect(jsonPath("$.longitude").value(-8.7538))
			.andExpect(jsonPath("$.capacity").value(10))
			.andExpect(jsonPath("$.dockedBikes").value(0))
			.andExpect(jsonPath("$.freeSlots").value(10))
			.andReturn()
			.getResponse();

		String id = JsonPath.read(result.getContentAsString(), "$.id");
		assertThat(result.getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/hubs/" + id);
	}

	@Requirement("RIA-56")
	@Test
	void shouldStoreRegisteredHubLinkedToItsHotel() throws Exception {
		var body = postHub(OPERATOR, validHotelHub())
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		var stored = jdbc.queryForMap(
			"select name, kind, hotel_id::text as hotel_id, capacity from hub where id = ?::uuid",
			(String) JsonPath.read(body, "$.id"));
		assertThat(stored)
			.containsEntry("name", NAME)
			.containsEntry("kind", "HOTEL")
			.containsEntry("hotel_id", MARES_PRAIA_ID);
		assertThat(((Number) stored.get("capacity")).intValue()).isEqualTo(10);
	}

	@Requirement("RIA-56")
	@Test
	void shouldRegisterTouristSpotHubWithoutHotel() throws Exception {
		postHub(OPERATOR, """
			{"name": "Miradouro das Salinas", "kind": "TOURIST_SPOT", "latitude": 40.6425, "longitude": -8.7479, "capacity": 6}
			""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("TOURIST_SPOT"))
			.andExpect(jsonPath("$.hotel").doesNotExist())
			.andExpect(jsonPath("$.capacity").value(6))
			.andExpect(jsonPath("$.freeSlots").value(6));

		assertThat(jdbc.queryForObject("select count(*) from hub where name = ? and hotel_id is null", Integer.class,
			"Miradouro das Salinas")).isEqualTo(1);
	}

	// Boundary values that are valid: capacity 1 and the limits of latitude and longitude.

	@Requirement("RIA-56")
	@Test
	void shouldRegisterHubWithCapacity1() throws Exception {
		postHub(OPERATOR, hotelHubWith("capacity", "1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.capacity").value(1))
			.andExpect(jsonPath("$.freeSlots").value(1));
	}

	@Requirement("RIA-56")
	@Test
	void shouldRegisterHubWithNameOf120Characters() throws Exception {
		var name = "A".repeat(120);

		postHub(OPERATOR, hotelHubWith("name", "\"" + name + "\""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(name));

		assertThat(hubsNamed(name)).isEqualTo(1);
	}

	@Requirement("RIA-56")
	@ParameterizedTest
	@CsvSource({ "-90, -180", "90, 180" })
	void shouldRegisterHubAtLimitsOfLatitudeAndLongitude(String latitude, String longitude) throws Exception {
		var json = hotelHubWith("latitude", latitude, "longitude", longitude);

		postHub(OPERATOR, json)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.latitude").value(Double.parseDouble(latitude)))
			.andExpect(jsonPath("$.longitude").value(Double.parseDouble(longitude)));
	}

	// Scenario "The operator cannot register a hub with capacity 0", and every other invalid field.

	@Requirement("RIA-56")
	@Test
	void shouldReturn400ProblemNamingCapacityWhenCapacityIs0() throws Exception {
		assertRejectedField(hotelHubWith("capacity", "0"), "capacity");
		assertThat(hubsNamed(NAME)).isZero();
	}

	@Requirement("RIA-56")
	@ParameterizedTest(name = "{0} = {1}")
	@CsvSource(nullValues = "MISSING", value = {
		"capacity, -1",
		"capacity, MISSING",
		"capacity, null",
		"capacity, 1.5",
		"name, MISSING",
		"name, '\"\"'",
		"name, '\"   \"'",
		"kind, MISSING",
		"kind, '\"BOAT\"'",
		"hotel, MISSING",
		"hotel, '\"Hotel Inexistente\"'",
		"latitude, MISSING",
		"latitude, 90.0001",
		"latitude, -90.0001",
		"longitude, MISSING",
		"longitude, 180.0001",
		"longitude, -180.0001",
	})
	void shouldReturn400ProblemNamingInvalidField(String field, String rawJsonValue) throws Exception {
		assertRejectedField(hotelHubWith(field, rawJsonValue), field);
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn400ProblemNamingNameWhenNameHas121Characters() throws Exception {
		assertRejectedField(hotelHubWith("name", "\"" + "B".repeat(121) + "\""), "name");
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn400ProblemNamingHotelWhenTouristSpotHasHotel() throws Exception {
		assertRejectedField(hotelHubWith("kind", "\"TOURIST_SPOT\""), "hotel");
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn400ProblemWhenBodyIsNotJson() throws Exception {
		postHub(OPERATOR, "{\"name\": ")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(hubs()).isZero();
	}

	// Scenario "The operator cannot register a hub with a name already in use".

	@Requirement("RIA-56")
	@Test
	void shouldReturn409ProblemAndKeepFirstHubWhenNameIsAlreadyInUse() throws Exception {
		postHub(OPERATOR, validHotelHub()).andExpect(status().isCreated());

		postHub(OPERATOR, hotelHubWith("latitude", "40.6410", "longitude", "-8.7540", "capacity", "6"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.title").value("Hub name already in use"));

		assertThat(jdbc.queryForList("select capacity from hub where name = ?", Integer.class, NAME))
			.containsExactly(10);
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn409ProblemWhenNameIsUsedByHubOfAnotherKind() throws Exception {
		postHub(OPERATOR, """
			{"name": "Miradouro das Salinas", "kind": "TOURIST_SPOT", "latitude": 40.6425, "longitude": -8.7479, "capacity": 6}
			""").andExpect(status().isCreated());

		postHub(ADMIN, hotelHubWith("name", "\"Miradouro das Salinas\""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Hub name already in use"));

		assertThat(hubsNamed("Miradouro das Salinas")).isEqualTo(1);
	}

	// Leading and trailing spaces are removed before the name is checked and stored.

	@Requirement("RIA-56")
	@Test
	void shouldRegisterHubWithNameStrippedOfSurroundingSpaces() throws Exception {
		postHub(OPERATOR, hotelHubWith("name", "\"  " + NAME + "  \""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(NAME));

		assertThat(hubsNamed(NAME)).isEqualTo(1);
		assertThat(hubs()).isEqualTo(1);
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn409ProblemWhenNameIsInUseOnceSurroundingSpacesAreRemoved() throws Exception {
		postHub(OPERATOR, validHotelHub()).andExpect(status().isCreated());

		postHub(OPERATOR, hotelHubWith("name", "\" " + NAME + "\""))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Hub name already in use"));

		assertThat(hubs()).isEqualTo(1);
	}

	// Decided by the Product Owner: names are compared exactly, so names that differ only in
	// letter case are different hubs.
	@Requirement("RIA-56")
	@Test
	void shouldRegisterHubWhoseNameDiffersOnlyInLetterCase() throws Exception {
		postHub(OPERATOR, validHotelHub()).andExpect(status().isCreated());

		postHub(OPERATOR, hotelHubWith("name", "\"MARÉS PRAIA – BEACH FRONT\""))
			.andExpect(status().isCreated());

		assertThat(hubs()).isEqualTo(2);
	}

	// The database enforces the rule too, so two concurrent registrations cannot both succeed.
	@Requirement("RIA-56")
	@Test
	void shouldEnforceUniqueHubNameInDatabase() {
		var uniqueIndexesOnNameOnly = jdbc.queryForObject("""
			select count(*)
			from pg_index i
			join pg_class t on t.oid = i.indrelid
			join pg_attribute a on a.attrelid = t.oid and a.attnum = i.indkey[0]
			where t.relname = 'hub' and i.indisunique and i.indnatts = 1 and i.indpred is null
			  and a.attname = 'name'
			""", Integer.class);

		assertThat(uniqueIndexesOnNameOnly).isEqualTo(1);
	}

	// Roles: OPERATOR and ADMIN may register hubs.

	@Requirement("RIA-56")
	@Test
	void shouldRegisterHubWhenAdminRegistersIt() throws Exception {
		postHub(ADMIN, validHotelHub())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(NAME));

		assertThat(hubsNamed(NAME)).isEqualTo(1);
	}

	// Scenario "A guest cannot register a hub": every other role gets 403.

	@Requirement("RIA-56")
	@Test
	void shouldReturn403ProblemWhenGuestRegistersHub() throws Exception {
		postHub(GUEST, validHotelHub())
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.title").value("Forbidden"));

		assertThat(hubsNamed(NAME)).isZero();
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn403ProblemWhenHotelManagerRegistersHub() throws Exception {
		postHub(HOTEL_MANAGER, validHotelHub())
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(hubsNamed(NAME)).isZero();
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn403ProblemWhenTechnicianRegistersHub() throws Exception {
		postHub(TECHNICIAN, validHotelHub())
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(hubsNamed(NAME)).isZero();
	}

	// Authorization comes before validation: a guest learns nothing about the input rules.
	@Requirement("RIA-56")
	@Test
	void shouldReturn403ProblemWhenGuestSendsInvalidHub() throws Exception {
		postHub(GUEST, hotelHubWith("capacity", "0"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-56")
	@Test
	void shouldReturn401ProblemWithoutToken() throws Exception {
		postHub(null, validHotelHub())
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));

		assertThat(hubsNamed(NAME)).isZero();
	}

}
