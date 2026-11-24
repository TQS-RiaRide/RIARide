package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.junit.jupiter.params.provider.CsvSource;
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

// Integration test (Failsafe): PUT /api/v1/vehicles/{id}/status (RIA-91) through the real security
// filter chain, with PostgreSQL in Testcontainers and only the JwtDecoder replaced. OPERATOR and
// ADMIN may activate, deactivate or retire a bike; every other role gets 403 before the body is
// read. A refused change is a 409 problem detail and leaves the stored bike as it was. A retired
// bike leaves its hub, so the guests' hub listing no longer shows it.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class ChangeVehicleStatusApiIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final UUID CENTRO = UUID.fromString("00000000-0000-0000-0000-00000000a091");

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void seedHub() {
    removeVehiclesAndHub();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Centro – RIA-91 test hub', 'TOURIST_SPOT', null, 40.6443, -8.6455, 10)
        """,
        CENTRO);
    for (var role : List.of("GUEST", "OPERATOR", "ADMIN", "HOTEL_MANAGER", "TECHNICIAN")) {
      when(jwtDecoder.decode(role)).thenReturn(jwt(role));
    }
  }

  // The hub and vehicle tables are shared with other tests, so nothing is left behind. A retired
  // bike has no hub any more, so this class's bikes are found by their code prefix.
  @AfterEach
  void removeVehiclesAndHub() {
    jdbc.update("delete from vehicle where code like 'RIA91-%'");
    jdbc.update("delete from hub where id = ?", CENTRO);
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

  // A bike docked at the test hub, except a bike in use, which has left its hub.
  private UUID bike(String code, String status) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', ?, ?, 80)
        """,
        id,
        code,
        status,
        "IN_USE".equals(status) ? null : CENTRO);
    return id;
  }

  private ResultActions changeStatus(String token, Object vehicleId, String json) throws Exception {
    var request =
        put("/api/v1/vehicles/{id}/status", vehicleId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mvc.perform(request);
  }

  private static String toStatus(String status) {
    return "{\"status\": \"" + status + "\"}";
  }

  private Map<String, Object> stored(UUID id) {
    return jdbc.queryForMap("select status, hub_id from vehicle where id = ?", id);
  }

  @Requirement("RIA-91")
  @ParameterizedTest
  @ValueSource(strings = {"OPERATOR", "ADMIN"})
  void shouldRetireAnAvailableBike(String role) throws Exception {
    var id = bike("RIA91-1", "AVAILABLE");

    changeStatus(role, id, toStatus("RETIRED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.code").value("RIA91-1"))
        .andExpect(jsonPath("$.status").value("RETIRED"))
        .andExpect(jsonPath("$.hubId").doesNotExist());

    assertThat(stored(id)).containsEntry("status", "RETIRED").containsEntry("hub_id", null);
  }

  @Requirement("RIA-91")
  @Test
  void shouldNoLongerShowARetiredBikeToGuests() throws Exception {
    var id = bike("RIA91-1", "AVAILABLE");
    bike("RIA91-2", "AVAILABLE");

    changeStatus("OPERATOR", id, toStatus("RETIRED")).andExpect(status().isOk());

    mvc.perform(
            get("/api/v1/hubs/{hubId}/vehicles", CENTRO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code").value(not(hasItem("RIA91-1"))))
        .andExpect(jsonPath("$[*].code").value(hasItem("RIA91-2")));
  }

  @Requirement("RIA-91")
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "AVAILABLE,   MAINTENANCE",
    "MAINTENANCE, AVAILABLE",
    "MAINTENANCE, RETIRED",
    "AVAILABLE,   AVAILABLE"
  })
  void shouldApplyAnOperatorTransition(String from, String to) throws Exception {
    var id = bike("RIA91-1", from);

    changeStatus("OPERATOR", id, toStatus(to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(to));

    assertThat(stored(id)).containsEntry("status", to);
  }

  @Requirement("RIA-91")
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "IN_USE,   RETIRED",
    "RESERVED, RETIRED",
    "RESERVED, MAINTENANCE",
    "RETIRED,  AVAILABLE"
  })
  void shouldRefuseAChangeOutsideTheOperatorTransitions(String from, String to) throws Exception {
    var id = bike("RIA91-1", from);
    var before = stored(id);

    changeStatus("OPERATOR", id, toStatus(to))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Status change refused"));

    assertThat(stored(id)).isEqualTo(before);
  }

  @Requirement("RIA-91")
  @Test
  void shouldExplainWhyABikeInUseCannotBeRetired() throws Exception {
    var id = bike("RIA91-1", "IN_USE");

    changeStatus("OPERATOR", id, toStatus("RETIRED"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value("A bike that is in use cannot be changed to retired."));
  }

  // Reserved and in use come from bookings and trips; an unknown value is not a status at all.
  @Requirement("RIA-91")
  @ParameterizedTest
  @ValueSource(strings = {"RESERVED", "IN_USE", "BROKEN"})
  void shouldRejectAStatusTheOperatorCannotSet(String target) throws Exception {
    var id = bike("RIA91-1", "AVAILABLE");

    changeStatus("OPERATOR", id, toStatus(target))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$['invalid-params'][0].name").value("status"));

    assertThat(stored(id)).containsEntry("status", "AVAILABLE");
  }

  @Requirement("RIA-91")
  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"status\": null}", "not json"})
  void shouldRejectARequestWithoutAStatus(String json) throws Exception {
    var id = bike("RIA91-1", "AVAILABLE");

    changeStatus("OPERATOR", id, json)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(stored(id)).containsEntry("status", "AVAILABLE");
  }

  @Requirement("RIA-91")
  @Test
  void shouldAnswerNotFoundForAnUnknownBike() throws Exception {
    changeStatus("OPERATOR", UUID.randomUUID(), toStatus("RETIRED"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Vehicle not found"));
  }

  @Requirement("RIA-91")
  @Test
  void shouldRejectAnIdThatIsNotAUuid() throws Exception {
    changeStatus("OPERATOR", "EB-701", toStatus("RETIRED"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  // Refused before the body is read: even an invalid body gets 403, so the caller learns nothing
  // about the input rules.
  @Requirement("RIA-91")
  @ParameterizedTest
  @ValueSource(strings = {"GUEST", "HOTEL_MANAGER", "TECHNICIAN"})
  void shouldForbidEveryOtherRole(String role) throws Exception {
    var id = bike("RIA91-1", "AVAILABLE");

    changeStatus(role, id, toStatus("RETIRED")).andExpect(status().isForbidden());
    changeStatus(role, id, "not json").andExpect(status().isForbidden());

    assertThat(stored(id)).containsEntry("status", "AVAILABLE");
  }

  @Requirement("RIA-91")
  @Test
  void shouldRequireAToken() throws Exception {
    var id = bike("RIA91-1", "AVAILABLE");

    changeStatus(null, id, toStatus("RETIRED")).andExpect(status().isUnauthorized());

    assertThat(stored(id)).containsEntry("status", "AVAILABLE");
  }
}
