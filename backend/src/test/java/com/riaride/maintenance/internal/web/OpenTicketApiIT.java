package com.riaride.maintenance.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import java.sql.Timestamp;
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

// Integration test (Failsafe): POST /api/v1/tickets (RIA-92) through the real security filter
// chain, with PostgreSQL in Testcontainers, a fixed clock and only the JwtDecoder replaced.
// OPERATOR
// and ADMIN open a ticket for a faulty bike: it is due 24 hours later and the bike leaves service,
// or, if it is in use, is flagged until its trip ends. Every other role gets 403 before the body is
// read. A refused request is a problem detail and leaves the bike and the tickets as they were.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class OpenTicketApiIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final UUID CENTRO = UUID.fromString("00000000-0000-0000-0000-00000000a092");

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void seedHub() {
    removeTicketsVehiclesAndHub();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Centro – RIA-92 test hub', 'TOURIST_SPOT', null, 40.6443, -8.6455, 10)
        """,
        CENTRO);
    for (var role : List.of("GUEST", "OPERATOR", "ADMIN", "HOTEL_MANAGER", "TECHNICIAN")) {
      when(jwtDecoder.decode(role)).thenReturn(jwt(role));
    }
  }

  // The tables are shared with other tests, so nothing is left behind; tickets go before their
  // bikes.
  @AfterEach
  void removeTicketsVehiclesAndHub() {
    jdbc.update(
        """
        delete from maintenance_ticket
        where vehicle_id in (select id from vehicle where code like 'RIA92-%')
        """);
    jdbc.update("delete from vehicle where code like 'RIA92-%'");
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

  // A bike docked at the test hub, except a bike in use or retired, which has no hub.
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
        List.of("IN_USE", "RETIRED").contains(status) ? null : CENTRO);
    return id;
  }

  private ResultActions openTicket(String token, String json) throws Exception {
    var request = post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(json);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mvc.perform(request);
  }

  private static String ticketFor(String code, String description) {
    return "{\"vehicleCode\": \"" + code + "\", \"description\": \"" + description + "\"}";
  }

  private Map<String, Object> storedBike(UUID id) {
    return jdbc.queryForMap(
        "select status, hub_id, maintenance_pending from vehicle where id = ?", id);
  }

  private List<Map<String, Object>> ticketsOf(UUID vehicleId) {
    return jdbc.queryForList(
        """
        select id, source, status, description, opened_at, sla_due_at
        from maintenance_ticket where vehicle_id = ?
        """,
        vehicleId);
  }

  @Requirement("RIA-92")
  @ParameterizedTest
  @ValueSource(strings = {"OPERATOR", "ADMIN"})
  void shouldOpenTicketDue24HoursLaterAndMoveAnAvailableBikeToMaintenance(String role)
      throws Exception {
    var bike = bike("RIA92-101", "AVAILABLE");

    openTicket(role, ticketFor("RIA92-101", "Rear brake does not work"))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.vehicleCode").value("RIA92-101"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.description").value("Rear brake does not work"))
        .andExpect(jsonPath("$.openedAt").value("2026-11-30T10:00:00Z"))
        .andExpect(jsonPath("$.slaDueAt").value("2026-12-01T10:00:00Z"))
        .andExpect(jsonPath("$.vehicleStatus").value("MAINTENANCE"))
        .andExpect(jsonPath("$.maintenancePending").value(false));

    assertThat(storedBike(bike))
        .containsEntry("status", "MAINTENANCE")
        .containsEntry("hub_id", CENTRO)
        .containsEntry("maintenance_pending", false);
    var tickets = ticketsOf(bike);
    assertThat(tickets).hasSize(1);
    assertThat(tickets.getFirst())
        .containsEntry("source", "OPERATOR")
        .containsEntry("status", "OPEN")
        .containsEntry("description", "Rear brake does not work")
        .containsEntry("opened_at", Timestamp.from(FixedClockConfiguration.NOW))
        .containsEntry("sla_due_at", Timestamp.from(Instant.parse("2026-12-01T10:00:00Z")));
  }

  @Requirement("RIA-92")
  @Test
  void shouldFindTheBikeWhateverTheCaseOfItsCode() throws Exception {
    var bike = bike("RIA92-102", "AVAILABLE");

    openTicket("OPERATOR", ticketFor("ria92-102", "Flat tyre"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vehicleCode").value("RIA92-102"));

    assertThat(storedBike(bike)).containsEntry("status", "MAINTENANCE");
  }

  @Requirement("RIA-92")
  @Test
  void shouldMoveAReservedBikeToMaintenance() throws Exception {
    var bike = bike("RIA92-103", "RESERVED");

    openTicket("OPERATOR", ticketFor("RIA92-103", "Handlebar is loose"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vehicleStatus").value("MAINTENANCE"));

    assertThat(storedBike(bike)).containsEntry("status", "MAINTENANCE");
  }

  // A bike in use cannot be moved to maintenance until its trip ends.
  @Requirement("RIA-92")
  @Test
  void shouldOpenTicketButKeepABikeInUseUntilItsTripEnds() throws Exception {
    var bike = bike("RIA92-104", "IN_USE");

    openTicket("OPERATOR", ticketFor("RIA92-104", "Guest reported a strange noise"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vehicleStatus").value("IN_USE"))
        .andExpect(jsonPath("$.maintenancePending").value(true))
        .andExpect(jsonPath("$.slaDueAt").value("2026-12-01T10:00:00Z"));

    assertThat(storedBike(bike))
        .containsEntry("status", "IN_USE")
        .containsEntry("maintenance_pending", true);
    assertThat(ticketsOf(bike)).hasSize(1);
  }

  @Requirement("RIA-92")
  @Test
  void shouldOpenTicketForABikeAlreadyInMaintenance() throws Exception {
    var bike = bike("RIA92-105", "MAINTENANCE");

    openTicket("OPERATOR", ticketFor("RIA92-105", "Battery does not charge"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vehicleStatus").value("MAINTENANCE"));

    assertThat(ticketsOf(bike)).hasSize(1);
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseATicketForARetiredBikeWith409() throws Exception {
    var bike = bike("RIA92-106", "RETIRED");
    var before = storedBike(bike);

    openTicket("OPERATOR", ticketFor("RIA92-106", "Flat tyre"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Ticket refused"));

    assertThat(storedBike(bike)).isEqualTo(before);
    assertThat(ticketsOf(bike)).isEmpty();
  }

  @Requirement("RIA-92")
  @Test
  void shouldAnswer404ForAnUnknownBike() throws Exception {
    openTicket("OPERATOR", ticketFor("RIA92-999", "Flat tyre"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Vehicle not found"));
  }

  @Requirement("RIA-92")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"vehicleCode\": \"RIA92-107\", \"description\": \"   \"}",
        "{\"vehicleCode\": \"RIA92-107\"}"
      })
  void shouldRefuseATicketWithoutADescriptionAndChangeNothing(String json) throws Exception {
    var bike = bike("RIA92-107", "AVAILABLE");

    openTicket("OPERATOR", json)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$['invalid-params'][0].name").value("description"));

    assertThat(storedBike(bike)).containsEntry("status", "AVAILABLE");
    assertThat(ticketsOf(bike)).isEmpty();
  }

  @Requirement("RIA-92")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"vehicleCode\": \"  \", \"description\": \"Flat tyre\"}",
        "{\"description\": \"Flat tyre\"}"
      })
  void shouldRefuseATicketWithoutABikeCode(String json) throws Exception {
    openTicket("OPERATOR", json)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$['invalid-params'][0].name").value("vehicleCode"));
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseABodyThatIsNotJson() throws Exception {
    openTicket("OPERATOR", "not json")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Requirement("RIA-92")
  @ParameterizedTest
  @ValueSource(strings = {"GUEST", "HOTEL_MANAGER", "TECHNICIAN"})
  void shouldRefuseOtherRolesWith403BeforeReadingTheBody(String role) throws Exception {
    var bike = bike("RIA92-108", "AVAILABLE");

    openTicket(role, "not json").andExpect(status().isForbidden());
    openTicket(role, ticketFor("RIA92-108", "Flat tyre")).andExpect(status().isForbidden());

    assertThat(storedBike(bike)).containsEntry("status", "AVAILABLE");
    assertThat(ticketsOf(bike)).isEmpty();
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseARequestWithoutATokenWith401() throws Exception {
    openTicket(null, ticketFor("RIA92-109", "Flat tyre")).andExpect(status().isUnauthorized());
  }
}
