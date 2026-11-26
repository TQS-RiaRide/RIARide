package com.riaride.trips.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
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

// Integration test (Failsafe): POST /api/v1/trips/{id}/end through the real security filter chain,
// with PostgreSQL in Testcontainers (RIA-55, BR10). Only the JwtDecoder and the MQTT commands are
// replaced, so the tests choose the caller and see the LOCK sent after commit. The trips in use are
// stored directly, as RIA-54 leaves them once telemetry confirms the unlock. Two ends at a hub with
// one free slot are settled by the hub row lock: one takes the slot, the other gets a 409. Today
// is fixed at 30 November 2026, 10:00 in Vila Ria.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class EndTripApiIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final String MARES_PRAIA = "00000000-0000-0000-0000-000000000002";
  private static final String MARES_SALINAS = "00000000-0000-0000-0000-000000000003";
  private static final String RITA = "rita-token";
  private static final String TOMAS = "tomas-token";
  private static final String OPERATOR = "operator-token";
  private static final String RITA_SUBJECT = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
  private static final String TOMAS_SUBJECT = "7b2f8a4c-5d3e-4f9a-8b1c-2d3e4f5a6b71";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private VehicleCommands vehicleCommands;

  private UUID centro;
  private UUID praia;
  private UUID salinas;
  private UUID ritasTrip;
  private UUID tomasTrip;

  // Marés Praia has room for 2 bikes and Marés Salinas for 8; each test docks the bikes it needs.
  @BeforeEach
  void hubsAndTripsInUse() {
    when(jwtDecoder.decode(RITA)).thenReturn(jwt(RITA, RITA_SUBJECT, "GUEST"));
    when(jwtDecoder.decode(TOMAS)).thenReturn(jwt(TOMAS, TOMAS_SUBJECT, "GUEST"));
    when(jwtDecoder.decode(OPERATOR))
        .thenReturn(jwt(OPERATOR, UUID.randomUUID().toString(), "OPERATOR"));
    centro = givenHub("Marés Centro", MARES_CENTRO, 40.6443, -8.6455, 12);
    praia = givenHub("Marés Praia", MARES_PRAIA, 40.6405, -8.7538, 2);
    salinas = givenHub("Marés Salinas", MARES_SALINAS, 40.6510, -8.6820, 8);
    ritasTrip = givenTripInUse(RITA_SUBJECT, "TRPA2345", "rita.almeida@example.test", "EB-301");
    tomasTrip = givenTripInUse(TOMAS_SUBJECT, "TRPB2345", "tomas.ferreira@example.test", "EB-302");
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

  private UUID givenHub(
      String name, String hotelId, double latitude, double longitude, int capacity) {
    var hubId = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, ?, 'HOTEL', ?::uuid, ?, ?, ?)
        """,
        hubId,
        name,
        hotelId,
        latitude,
        longitude,
        capacity);
    return hubId;
  }

  private void givenDockedBikes(UUID hubId, int count) {
    for (var i = 0; i < count; i++) {
      jdbc.update(
          """
          insert into vehicle (id, code, type, status, hub_id, battery_pct)
          values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80)
          """,
          UUID.randomUUID(),
          "DK-" + UUID.randomUUID().toString().substring(0, 8),
          hubId);
    }
  }

  // A guest with an active stay and an active stay pass, riding a bike taken from Marés Centro 40
  // minutes ago: the bike is in use and off its hub, the reservation used and the trip in use.
  private UUID givenTripInUse(String keycloakId, String stayCode, String email, String code) {
    var stayId = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Rita Almeida', ?, date '2026-11-28', date '2026-12-02', 'ACTIVE')
        """,
        stayId,
        MARES_CENTRO,
        stayCode);
    var guestId = UUID.randomUUID();
    jdbc.update(
        "insert into guest (id, keycloak_id, stay_id, email, locale) values (?, ?, ?, ?, 'pt')",
        guestId,
        keycloakId,
        stayId,
        email);
    var passId = UUID.randomUUID();
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents,
                          deposit_status, activated_at)
        values (?, ?, 'STAY', 'ACTIVE', 4000, 5000, 'HELD', timestamptz '2026-11-30T09:20:00Z')
        """,
        passId,
        guestId);
    var vehicleId = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', 'IN_USE', null, 80)
        """,
        vehicleId,
        code);
    var reservationId = UUID.randomUUID();
    jdbc.update(
        """
        insert into reservation (id, pass_id, vehicle_id, vehicle_code, hub_id, status, created_at,
                                 expires_at)
        values (?, ?, ?, ?, ?, 'USED', timestamptz '2026-11-30T09:20:00Z',
                timestamptz '2026-11-30T09:35:00Z')
        """,
        reservationId,
        passId,
        vehicleId,
        code,
        centro);
    var tripId = UUID.randomUUID();
    jdbc.update(
        """
        insert into trip (id, guest_id, pass_id, reservation_id, vehicle_id, vehicle_code, hub_id,
                          status, requested_at, started_at)
        values (?, ?, ?, ?, ?, ?, ?, 'IN_USE', timestamptz '2026-11-30T09:20:00Z',
                timestamptz '2026-11-30T09:20:05Z')
        """,
        tripId,
        guestId,
        passId,
        reservationId,
        vehicleId,
        code,
        centro);
    return tripId;
  }

  private ResultActions end(String token, UUID tripId, String body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/trips/{id}/end", tripId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions end(String token, UUID tripId, UUID hubId) throws Exception {
    return end(token, tripId, "{\"hubId\": \"" + hubId + "\"}");
  }

  private Map<String, Object> bike(String code) {
    return jdbc.queryForMap("select status, hub_id from vehicle where code = ?", code);
  }

  private String tripStatus(UUID tripId) {
    return jdbc.queryForObject("select status from trip where id = ?", String.class, tripId);
  }

  @Requirement("RIA-55")
  @Test
  void shouldEndTheTripAtAHubWithAFreeSlot() throws Exception {
    givenDockedBikes(praia, 1);

    end(RITA, ritasTrip, praia)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ritasTrip.toString()))
        .andExpect(jsonPath("$.status").value("ENDED"))
        .andExpect(jsonPath("$.vehicleCode").value("EB-301"))
        .andExpect(jsonPath("$.startedAt").value("2026-11-30T09:20:05Z"))
        .andExpect(jsonPath("$.endedAt").value("2026-11-30T10:00:00Z"))
        .andExpect(jsonPath("$.endHubId").value(praia.toString()));

    assertThat(bike("EB-301")).containsEntry("status", "AVAILABLE").containsEntry("hub_id", praia);
    assertThat(tripStatus(ritasTrip)).isEqualTo("ENDED");
    verify(vehicleCommands).lock("EB-301", praia.toString());
  }

  // The example of the RIA-55 feature: Marés Praia is full, so the trip continues, and Marés
  // Salinas (about 6.2 km away) is suggested before Marés Centro (about 9.1 km away).
  @Requirement("RIA-55")
  @Test
  void shouldRefuseAFullHubWith409AndTheNearestHubsWithAFreeSlot() throws Exception {
    givenDockedBikes(praia, 2);
    givenDockedBikes(salinas, 3);
    givenDockedBikes(centro, 6);

    end(RITA, ritasTrip, praia)
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Hub full"))
        .andExpect(
            jsonPath("$.nearbyHubs[*].name").value(contains("Marés Salinas", "Marés Centro")))
        .andExpect(
            jsonPath("$.nearbyHubs[*].hubId")
                .value(contains(salinas.toString(), centro.toString())))
        .andExpect(jsonPath("$.nearbyHubs[*].freeSlots").value(contains(5, 6)))
        .andExpect(jsonPath("$.nearbyHubs[0].distanceMeters").value(6169));

    assertThat(tripStatus(ritasTrip)).isEqualTo("IN_USE");
    assertThat(bike("EB-301")).containsEntry("status", "IN_USE").containsEntry("hub_id", null);
    verify(vehicleCommands, never()).lock(any(), any());
  }

  // BR10 under concurrency: two guests end their trips at the same moment at a hub with one free
  // slot. The hub row lock lets one take it; the other is a 409 and keeps riding.
  @Requirement("RIA-55")
  @Test
  void onlyOneOfTwoParallelEndsTakesTheLastFreeSlot() throws Exception {
    givenDockedBikes(praia, 1);

    var statuses = endTogether(RITA, ritasTrip, TOMAS, tomasTrip, praia);

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from vehicle where hub_id = ?", Integer.class, praia))
        .isEqualTo(2);
    assertThat(List.of(tripStatus(ritasTrip), tripStatus(tomasTrip)))
        .containsExactlyInAnyOrder("ENDED", "IN_USE");
  }

  // The same trip ended twice at the same moment (a double tap): the vehicle row lock lets one
  // request dock the bike; the other finds it no longer in use and is a 409, not a 500.
  @Requirement("RIA-55")
  @Test
  void onlyOneOfTwoParallelEndsOfTheSameTripSucceeds() throws Exception {
    var statuses = endTogether(RITA, ritasTrip, RITA, ritasTrip, praia);

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(tripStatus(ritasTrip)).isEqualTo("ENDED");
    assertThat(bike("EB-301")).containsEntry("status", "AVAILABLE").containsEntry("hub_id", praia);
    verify(vehicleCommands).lock("EB-301", praia.toString());
  }

  private List<Integer> endTogether(
      String firstToken, UUID firstTrip, String secondToken, UUID secondTrip, UUID hubId)
      throws Exception {
    var go = new CountDownLatch(1);
    try (var threads = Executors.newFixedThreadPool(2)) {
      var first =
          threads.submit(() -> {
            go.await();
            return end(firstToken, firstTrip, hubId).andReturn().getResponse().getStatus();
          });
      var second =
          threads.submit(() -> {
            go.await();
            return end(secondToken, secondTrip, hubId).andReturn().getResponse().getStatus();
          });
      go.countDown();
      return List.of(first.get(), second.get());
    }
  }

  @Requirement("RIA-55")
  @Test
  void shouldRefuseToEndATripTwice() throws Exception {
    end(RITA, ritasTrip, praia).andExpect(status().isOk());

    end(RITA, ritasTrip, salinas)
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Trip not in progress"));

    assertThat(bike("EB-301")).containsEntry("hub_id", praia);
  }

  @Requirement("RIA-55")
  @Test
  void shouldRefuseATripThatIsStillStarting() throws Exception {
    jdbc.update("update trip set status = 'STARTING', started_at = null where id = ?", ritasTrip);

    end(RITA, ritasTrip, praia)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Trip not in progress"));
  }

  @Requirement("RIA-55")
  @Test
  void shouldReturn404ProblemForAnUnknownHub() throws Exception {
    end(RITA, ritasTrip, UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Hub not found"));

    assertThat(tripStatus(ritasTrip)).isEqualTo("IN_USE");
  }

  @Requirement("RIA-55")
  @Test
  void shouldNotEndAnotherGuestsTrip() throws Exception {
    end(TOMAS, ritasTrip, praia)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Trip not found"));

    assertThat(tripStatus(ritasTrip)).isEqualTo("IN_USE");
  }

  @Requirement("RIA-55")
  @Test
  void shouldReturn400ProblemWithoutAHub() throws Exception {
    end(RITA, ritasTrip, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(tripStatus(ritasTrip)).isEqualTo("IN_USE");
  }

  @Requirement("RIA-55")
  @Test
  void shouldRefuseOperatorWith403() throws Exception {
    end(OPERATOR, ritasTrip, praia)
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(tripStatus(ritasTrip)).isEqualTo("IN_USE");
  }
}
