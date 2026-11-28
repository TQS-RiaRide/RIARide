package com.riaride.fleet.internal.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

// Integration test (Failsafe): GET /api/v1/hubs (RIA-107) through the real security filter chain,
// with PostgreSQL in Testcontainers. Every platform role reads the hub list; each hub has its
// hotel's name, its capacity, the bikes docked there and its free slots, by name.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class ListHubsApiIT {

  private static final String MARES_PRAIA = "00000000-0000-0000-0000-000000000002";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void twoHubs() {
    for (var role : List.of("GUEST", "OPERATOR", "TECHNICIAN", "HOTEL_MANAGER", "ADMIN")) {
      when(jwtDecoder.decode(role)).thenReturn(jwt(role));
    }
    var praia = hub("Marés Praia", "HOTEL", MARES_PRAIA, 10);
    hub("Miradouro da Ria", "TOURIST_SPOT", null, 6);
    for (var i = 1; i <= 3; i++) {
      jdbc.update(
          """
          insert into vehicle (id, code, type, status, hub_id, battery_pct)
          values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80)
          """,
          UUID.randomUUID(),
          "EB-10" + i,
          praia);
    }
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from vehicle");
    jdbc.update("delete from hub");
  }

  private UUID hub(String name, String kind, String hotelId, int capacity) {
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

  private static Jwt jwt(String role) {
    return Jwt.withTokenValue(role)
        .header("alg", "RS256")
        .subject(UUID.randomUUID().toString())
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", Map.of("roles", List.of(role)))
        .build();
  }

  @Requirement("RIA-107")
  @Test
  void shouldListEveryHubByNameWithItsFreeSlots() throws Exception {
    mvc.perform(get("/api/v1/hubs").header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Marés Praia"))
        .andExpect(jsonPath("$[0].kind").value("HOTEL"))
        .andExpect(jsonPath("$[0].hotel").value("Marés Praia"))
        .andExpect(jsonPath("$[0].capacity").value(10))
        .andExpect(jsonPath("$[0].dockedBikes").value(3))
        .andExpect(jsonPath("$[0].freeSlots").value(7))
        .andExpect(jsonPath("$[1].name").value("Miradouro da Ria"))
        .andExpect(jsonPath("$[1].hotel").doesNotExist())
        .andExpect(jsonPath("$[1].dockedBikes").value(0))
        .andExpect(jsonPath("$[1].freeSlots").value(6));
  }

  @Requirement("RIA-107")
  @ParameterizedTest(name = "the role {0} reads the hub list")
  @ValueSource(strings = {"OPERATOR", "TECHNICIAN", "HOTEL_MANAGER", "ADMIN"})
  void shouldLetEveryPlatformRoleReadTheList(String role) throws Exception {
    mvc.perform(get("/api/v1/hubs").header(HttpHeaders.AUTHORIZATION, "Bearer " + role))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Requirement("RIA-107")
  @Test
  void shouldRefuseARequestWithoutAToken() throws Exception {
    mvc.perform(get("/api/v1/hubs")).andExpect(status().isUnauthorized());
  }
}
