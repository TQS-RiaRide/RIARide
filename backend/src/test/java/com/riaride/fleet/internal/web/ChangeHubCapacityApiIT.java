package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
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
import org.springframework.test.web.servlet.ResultActions;

// Integration test (Failsafe): PUT /api/v1/hubs/{id}/capacity (RIA-96) through the real security
// filter chain, with PostgreSQL in Testcontainers. Only the JwtDecoder is replaced, so the tests
// control the role and the hotel_id claim. A hotel manager changes the hubs of their hotel and the
// tourist spots, an admin any hub, and every other role gets 403. The bikes docked must still fit.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class ChangeHubCapacityApiIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final String MARES_PRAIA = "00000000-0000-0000-0000-000000000002";

  private static final String PRAIA_MANAGER = "praia-manager-token";
  private static final String CENTRO_MANAGER = "centro-manager-token";
  private static final String MANAGER_WITHOUT_HOTEL = "manager-without-hotel-token";
  private static final String ADMIN = "admin-token";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  private UUID praia;
  private UUID touristSpot;

  // Marés Praia has room for 10 bikes and 8 docked, as in the acceptance criterion.
  @BeforeEach
  void hubs() {
    when(jwtDecoder.decode(PRAIA_MANAGER))
        .thenReturn(jwt(PRAIA_MANAGER, "HOTEL_MANAGER", MARES_PRAIA));
    when(jwtDecoder.decode(CENTRO_MANAGER))
        .thenReturn(jwt(CENTRO_MANAGER, "HOTEL_MANAGER", MARES_CENTRO));
    when(jwtDecoder.decode(MANAGER_WITHOUT_HOTEL))
        .thenReturn(jwt(MANAGER_WITHOUT_HOTEL, "HOTEL_MANAGER", null));
    when(jwtDecoder.decode(ADMIN)).thenReturn(jwt(ADMIN, "ADMIN", null));
    for (var role : List.of("GUEST", "OPERATOR", "TECHNICIAN")) {
      when(jwtDecoder.decode(role)).thenReturn(jwt(role, role, MARES_PRAIA));
    }
    praia = givenHub("Marés Praia", "HOTEL", MARES_PRAIA, 10);
    touristSpot = givenHub("Miradouro da Ria", "TOURIST_SPOT", null, 4);
    for (var i = 1; i <= 8; i++) {
      jdbc.update(
          """
          insert into vehicle (id, code, type, status, hub_id, battery_pct)
          values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80)
          """,
          UUID.randomUUID(),
          "EB-90" + i,
          praia);
    }
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from vehicle");
    jdbc.update("delete from hub");
  }

  private static Jwt jwt(String token, String role, String hotelId) {
    var builder =
        Jwt.withTokenValue(token)
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

  private UUID givenHub(String name, String kind, String hotelId, int capacity) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, ?, ?, ?::uuid, 40.64, -8.65, ?)
        """,
        id,
        name,
        kind,
        hotelId,
        capacity);
    return id;
  }

  private ResultActions putCapacity(String token, UUID hubId, String json) throws Exception {
    var request =
        put("/api/v1/hubs/{id}/capacity", hubId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mvc.perform(request);
  }

  private int capacityOf(UUID hubId) {
    return jdbc.queryForObject("select capacity from hub where id = ?", Integer.class, hubId);
  }

  // The acceptance criterion: 8 bikes and a capacity changed to 8, so the hub is full.
  @Requirement("RIA-96")
  @Test
  void shouldLetTheManagerOfTheHotelSetTheCapacityToTheBikesDocked() throws Exception {
    putCapacity(PRAIA_MANAGER, praia, "{\"capacity\": 8}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(praia.toString()))
        .andExpect(jsonPath("$.name").value("Marés Praia"))
        .andExpect(jsonPath("$.capacity").value(8))
        .andExpect(jsonPath("$.dockedBikes").value(8))
        .andExpect(jsonPath("$.freeSlots").value(0));

    assertThat(capacityOf(praia)).isEqualTo(8);
  }

  @Requirement("RIA-96")
  @Test
  void shouldLetTheAdminChangeAnyHub() throws Exception {
    putCapacity(ADMIN, praia, "{\"capacity\": 14}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freeSlots").value(6));

    assertThat(capacityOf(praia)).isEqualTo(14);
  }

  @Requirement("RIA-96")
  @Test
  void shouldLetAnyHotelManagerChangeATouristSpot() throws Exception {
    putCapacity(CENTRO_MANAGER, touristSpot, "{\"capacity\": 6}").andExpect(status().isOk());

    assertThat(capacityOf(touristSpot)).isEqualTo(6);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseACapacityBelowTheBikesDocked() throws Exception {
    putCapacity(PRAIA_MANAGER, praia, "{\"capacity\": 7}")
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Capacity below docked bikes"))
        .andExpect(jsonPath("$.dockedBikes").value(8));

    assertThat(capacityOf(praia)).isEqualTo(10);
  }

  @Requirement("RIA-96")
  @ParameterizedTest(name = "a manager with token {0} cannot change Marés Praia")
  @ValueSource(strings = {CENTRO_MANAGER, MANAGER_WITHOUT_HOTEL})
  void shouldRefuseAManagerWhoDoesNotManageTheHotel(String token) throws Exception {
    putCapacity(token, praia, "{\"capacity\": 12}")
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(capacityOf(praia)).isEqualTo(10);
  }

  @Requirement("RIA-96")
  @ParameterizedTest(name = "the role {0} cannot change a hub's capacity")
  @ValueSource(strings = {"GUEST", "OPERATOR", "TECHNICIAN"})
  void shouldRefuseOtherRoles(String role) throws Exception {
    putCapacity(role, praia, "{\"capacity\": 12}").andExpect(status().isForbidden());

    assertThat(capacityOf(praia)).isEqualTo(10);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseARequestWithoutAToken() throws Exception {
    putCapacity(null, praia, "{\"capacity\": 12}").andExpect(status().isUnauthorized());
  }

  @Requirement("RIA-96")
  @Test
  void shouldAnswer404ForAnUnknownHub() throws Exception {
    putCapacity(ADMIN, UUID.randomUUID(), "{\"capacity\": 12}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Hub not found"));
  }

  // The value is never repeated in the problem detail (QA Manual 3.4).
  @Requirement("RIA-96")
  @ParameterizedTest(name = "the body {0} is refused")
  @ValueSource(
      strings = {
        "{\"capacity\": 0}",
        "{\"capacity\": -3}",
        "{}",
        "{\"capacity\": \"ten\"}",
        "{\"capacity\": 2.5}"
      })
  void shouldRefuseAnInvalidCapacity(String json) throws Exception {
    putCapacity(ADMIN, praia, json)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid hub"))
        .andExpect(jsonPath("$['invalid-params'][*].name", hasItem("capacity")));

    assertThat(capacityOf(praia)).isEqualTo(10);
  }
}
