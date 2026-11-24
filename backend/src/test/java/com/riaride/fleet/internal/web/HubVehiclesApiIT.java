package com.riaride.fleet.internal.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Integration test (Failsafe): GET /api/v1/hubs/{hubId}/vehicles (RIA-59) through the real security
// filter chain, with PostgreSQL in Testcontainers and only the JwtDecoder replaced. Each vehicle is
// marked bookable by BR12; a bike below 20 % stays "available" but is not bookable.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class HubVehiclesApiIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a059");

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void seedHubWithVehicles() {
    removeVehiclesAndHubs();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Miradouro da Ria', 'TOURIST_SPOT', null, 40.64, -8.65, 6)
        """,
        HUB);
    insertVehicle("EB-601", "AVAILABLE", 18);
    insertVehicle("EB-602", "AVAILABLE", 20);
    insertVehicle("EB-603", "MAINTENANCE", 90);
    insertVehicle("EB-604", "AVAILABLE", null);
    for (var role : List.of("GUEST", "OPERATOR", "ADMIN", "HOTEL_MANAGER", "TECHNICIAN")) {
      when(jwtDecoder.decode(role)).thenReturn(jwt(role));
    }
  }

  // The hub table is shared with other tests that delete hubs, so no vehicle is left behind.
  @AfterEach
  void removeVehiclesAndHubs() {
    jdbc.update("delete from vehicle");
    jdbc.update("delete from hub where id = ?", HUB);
  }

  private void insertVehicle(String code, String status, Integer batteryPct) {
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct, last_telemetry_at)
        values (?, ?, 'E_BIKE', ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        code,
        status,
        HUB,
        batteryPct,
        batteryPct == null ? null : java.sql.Timestamp.from(Instant.parse("2026-11-30T10:00:00Z")));
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

  @Requirement("RIA-59")
  @Test
  void shouldListTheHubsVehiclesWithOnlyThoseWithEnoughBatteryBookable() throws Exception {
    mvc.perform(
            get("/api/v1/hubs/{hubId}/vehicles", HUB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].code")
                .value(org.hamcrest.Matchers.contains("EB-601", "EB-602", "EB-603", "EB-604")))
        .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
        .andExpect(jsonPath("$[0].batteryPct").value(18))
        .andExpect(jsonPath("$[0].bookable").value(false))
        .andExpect(jsonPath("$[1].bookable").value(true))
        .andExpect(jsonPath("$[2].bookable").value(false))
        .andExpect(jsonPath("$[3].batteryPct").doesNotExist())
        .andExpect(jsonPath("$[3].bookable").value(false));
  }

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(strings = {"OPERATOR", "ADMIN"})
  void shouldLetOperatorsAndAdminsListTheVehicles(String role) throws Exception {
    mvc.perform(
            get("/api/v1/hubs/{hubId}/vehicles", HUB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + role))
        .andExpect(status().isOk());
  }

  @Requirement("RIA-59")
  @ParameterizedTest
  @ValueSource(strings = {"HOTEL_MANAGER", "TECHNICIAN"})
  void shouldRefuseOtherRoles(String role) throws Exception {
    mvc.perform(
            get("/api/v1/hubs/{hubId}/vehicles", HUB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + role))
        .andExpect(status().isForbidden());
  }

  @Requirement("RIA-59")
  @Test
  void shouldRequireAToken() throws Exception {
    mvc.perform(get("/api/v1/hubs/{hubId}/vehicles", HUB)).andExpect(status().isUnauthorized());
  }

  @Requirement("RIA-59")
  @Test
  void shouldAnswer404ProblemForAnUnknownHub() throws Exception {
    mvc.perform(
            get("/api/v1/hubs/{hubId}/vehicles", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Hub not found"));
  }

  @Requirement("RIA-59")
  @Test
  void shouldAnswer400ProblemForAHubIdThatIsNotAUuid() throws Exception {
    mvc.perform(
            get("/api/v1/hubs/{hubId}/vehicles", "not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }
}
