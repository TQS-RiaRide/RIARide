package com.riaride.passes.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Integration test (Failsafe): GET and PUT /api/v1/usage-rules (RIA-96) through the real security
// filter chain, with PostgreSQL in Testcontainers. Hotel managers and admins read and change the
// rules of use; every other role gets 403. A change applies from the FixedClock's NOW. The rules
// a test adds are removed after it, so the seeded 3 h stays in force for the other tests that
// share the database.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class UsageRuleApiIT {

  private static final String RULES = "/api/v1/usage-rules";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SEEDED_RULE = "00000000-0000-0000-0000-000000000096";

  private static final String HOTEL_MANAGER = "HOTEL_MANAGER";
  private static final String ADMIN = "ADMIN";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void tokens() {
    for (var role : List.of(HOTEL_MANAGER, ADMIN, "GUEST", "OPERATOR", "TECHNICIAN")) {
      when(jwtDecoder.decode(role)).thenReturn(jwt(role));
    }
  }

  @AfterEach
  void keepOnlyTheSeededRule() {
    jdbc.update("delete from usage_rule where id <> ?::uuid", SEEDED_RULE);
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

  private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder req) {
    return role == null ? req : req.header(HttpHeaders.AUTHORIZATION, "Bearer " + role);
  }

  private static MockHttpServletRequestBuilder change(String json) {
    return put(RULES).contentType(MediaType.APPLICATION_JSON).content(json);
  }

  private int rules() {
    return jdbc.queryForObject("select count(*) from usage_rule", Integer.class);
  }

  @Requirement("RIA-96")
  @Test
  void shouldGiveTheSeededMaximumTripDurationOf3Hours() throws Exception {
    mvc.perform(as(HOTEL_MANAGER, get(RULES)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxTripDurationMinutes").value(180))
        .andExpect(jsonPath("$.effectiveFrom").value("1970-01-01T00:00:00Z"));
  }

  // The acceptance criterion changes it to 2 hours; it applies from now on.
  @Requirement("RIA-96")
  @ParameterizedTest(name = "the role {0} changes the maximum trip duration")
  @ValueSource(strings = {HOTEL_MANAGER, ADMIN})
  void shouldChangeTheMaximumTripDurationFromNow(String role) throws Exception {
    mvc.perform(as(role, change("{\"maxTripDurationMinutes\": 120}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxTripDurationMinutes").value(120))
        .andExpect(jsonPath("$.effectiveFrom").value(FixedClockConfiguration.NOW.toString()));

    mvc.perform(as(role, get(RULES))).andExpect(jsonPath("$.maxTripDurationMinutes").value(120));
    // The earlier rule is kept, for the trips that started under it.
    assertThat(rules()).isEqualTo(2);
  }

  // A second change at the same instant (the FixedClock never moves) is refused, not lost.
  @Requirement("RIA-96")
  @Test
  void shouldRefuseASecondChangeAtTheSameInstant() throws Exception {
    mvc.perform(as(ADMIN, change("{\"maxTripDurationMinutes\": 120}"))).andExpect(status().isOk());

    mvc.perform(as(HOTEL_MANAGER, change("{\"maxTripDurationMinutes\": 150}")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Rules of use changed concurrently"));

    mvc.perform(as(ADMIN, get(RULES))).andExpect(jsonPath("$.maxTripDurationMinutes").value(120));
  }

  @Requirement("RIA-96")
  @ParameterizedTest(name = "the role {0} cannot read or change the rules of use")
  @ValueSource(strings = {"GUEST", "OPERATOR", "TECHNICIAN"})
  void shouldRefuseOtherRoles(String role) throws Exception {
    mvc.perform(as(role, get(RULES))).andExpect(status().isForbidden());
    mvc.perform(as(role, change("{\"maxTripDurationMinutes\": 120}")))
        .andExpect(status().isForbidden());

    assertThat(rules()).isEqualTo(1);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseARequestWithoutAToken() throws Exception {
    mvc.perform(as(null, get(RULES))).andExpect(status().isUnauthorized());
  }

  // The value is never repeated in the problem detail (QA Manual 3.4).
  @Requirement("RIA-96")
  @ParameterizedTest(name = "the body {0} is refused")
  @ValueSource(
      strings = {
        "{\"maxTripDurationMinutes\": 14}",
        "{\"maxTripDurationMinutes\": 1441}",
        "{}",
        "{\"maxTripDurationMinutes\": \"two hours\"}",
        "{\"maxTripDurationMinutes\": 90.5}"
      })
  void shouldRefuseAnInvalidMaximumTripDuration(String json) throws Exception {
    mvc.perform(as(ADMIN, change(json)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid rule of use"))
        .andExpect(jsonPath("$['invalid-params'][*].name", hasItem("maxTripDurationMinutes")));

    assertThat(rules()).isEqualTo(1);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseABodyThatIsNotJson() throws Exception {
    mvc.perform(as(ADMIN, change("not json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid rule of use"));
  }
}
