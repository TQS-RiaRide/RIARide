package com.riaride.accounts.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Integration test (Failsafe): POST /api/v1/stays through the real security filter chain,
// with PostgreSQL in Testcontainers. Only the JwtDecoder is replaced, so the tests control
// which roles and hotel the bearer token carries without a running Keycloak.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegisterStayApiIT {

	private static final String STAYS = "/api/v1/stays";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
	private static final String MARES_PRAIA = "00000000-0000-0000-0000-000000000002";

	private static final String MANAGER_PRAIA = "manager-praia-token";
	private static final String MANAGER_NO_HOTEL = "manager-no-hotel-token";
	private static final String ADMIN = "admin-token";
	private static final String GUEST = "guest-token";
	private static final String INVALID = "invalid-token";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void setUpTokens() {
		when(jwtDecoder.decode(MANAGER_PRAIA)).thenReturn(jwt(MANAGER_PRAIA, "HOTEL_MANAGER", MARES_PRAIA));
		when(jwtDecoder.decode(MANAGER_NO_HOTEL)).thenReturn(jwt(MANAGER_NO_HOTEL, "HOTEL_MANAGER", null));
		when(jwtDecoder.decode(ADMIN)).thenReturn(jwt(ADMIN, "ADMIN", null));
		when(jwtDecoder.decode(GUEST)).thenReturn(jwt(GUEST, "GUEST", null));
		when(jwtDecoder.decode(INVALID)).thenThrow(new BadJwtException("signature does not match"));
	}

	private static Jwt jwt(String token, String role, String hotelId) {
		var builder = Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("realm_access", Map.of("roles", List.of(role)));
		if (hotelId != null) {
			builder.claim("hotel_id", hotelId);
		}
		return builder.build();
	}

	private static String body(String hotelId, String guestName, String checkIn, String checkOut) {
		return """
			{"hotelId": "%s", "guestName": "%s", "checkIn": "%s", "checkOut": "%s"}
			""".formatted(hotelId, guestName, checkIn, checkOut);
	}

	private ResultActions postStay(String token, String json) throws Exception {
		var request = post(STAYS).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mvc.perform(request);
	}

	private int staysFor(String guestName) {
		return jdbc.queryForObject("select count(*) from stay where guest_name = ?", Integer.class, guestName);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRegisterStayAndReturnStayCodeWhenManagerRegistersAtOwnHotel() throws Exception {
		postStay(MANAGER_PRAIA, body(MARES_PRAIA, "Rita Almeida", "2026-12-01", "2026-12-05"))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/v1/stays/[0-9a-f-]{36}$")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.hotelId").value(MARES_PRAIA))
			.andExpect(jsonPath("$.guestName").value("Rita Almeida"))
			.andExpect(jsonPath("$.checkIn").value("2026-12-01"))
			.andExpect(jsonPath("$.checkOut").value("2026-12-05"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.stayCode").value(matchesPattern("^[A-HJ-NP-Z2-9]{8}$")));

		assertThat(staysFor("Rita Almeida")).isEqualTo(1);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRegisterStayAtAnyHotelWhenAdmin() throws Exception {
		postStay(ADMIN, body(MARES_CENTRO, "Inês Duarte", "2026-12-01", "2026-12-03"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.hotelId").value(MARES_CENTRO));
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn401ProblemWithoutToken() throws Exception {
		postStay(null, body(MARES_PRAIA, "Sem Token", "2026-12-01", "2026-12-05"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));

		assertThat(staysFor("Sem Token")).isZero();
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn401ProblemWithInvalidToken() throws Exception {
		postStay(INVALID, body(MARES_PRAIA, "Token Inválido", "2026-12-01", "2026-12-05"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn403ProblemWhenGuestRegistersStay() throws Exception {
		postStay(GUEST, body(MARES_PRAIA, "Tomás Ferreira", "2026-12-01", "2026-12-05"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));

		assertThat(staysFor("Tomás Ferreira")).isZero();
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn403ProblemWhenManagerRegistersAtAnotherHotel() throws Exception {
		postStay(MANAGER_PRAIA, body(MARES_CENTRO, "Outro Hotel", "2026-12-01", "2026-12-05"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

		assertThat(staysFor("Outro Hotel")).isZero();
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn403ProblemWhenManagerTokenHasNoHotel() throws Exception {
		postStay(MANAGER_NO_HOTEL, body(MARES_PRAIA, "Sem Hotel", "2026-12-01", "2026-12-05"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn404ProblemWhenHotelDoesNotExist() throws Exception {
		postStay(ADMIN, body("00000000-0000-0000-0000-0000000000ff", "Hotel Inexistente", "2026-12-01", "2026-12-05"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn422ProblemWhenCheckOutIsNotAfterCheckIn() throws Exception {
		postStay(MANAGER_PRAIA, body(MARES_PRAIA, "Datas Trocadas", "2026-12-05", "2026-12-01"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(422));

		assertThat(staysFor("Datas Trocadas")).isZero();
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn400ProblemWhenGuestNameIsBlank() throws Exception {
		postStay(MANAGER_PRAIA, body(MARES_PRAIA, " ", "2026-12-01", "2026-12-05"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn400ProblemWhenCheckInIsMissing() throws Exception {
		postStay(MANAGER_PRAIA, """
			{"hotelId": "%s", "guestName": "Sem Data", "checkOut": "2026-12-05"}
			""".formatted(MARES_PRAIA))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Requirement("RIA-50")
	@Test
	void shouldReturn400ProblemWhenDateIsMalformed() throws Exception {
		postStay(MANAGER_PRAIA, body(MARES_PRAIA, "Data Errada", "01/12/2026", "2026-12-05"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

}
