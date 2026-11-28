package com.riaride.trips.internal.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

// Integration test (Failsafe): GET /api/v1/trips (RIA-107) through the real security filter chain,
// with PostgreSQL in Testcontainers. A guest lists their own trips, active and past, newest first,
// for the dashboard; another guest's trips never appear, and a guest with none gets an empty list.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class ListTripsApiIT {

  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final String RITA = "rita-token";
  private static final String TOMAS = "tomas-token";
  private static final String RITA_SUBJECT = UUID.randomUUID().toString();
  private static final String TOMAS_SUBJECT = UUID.randomUUID().toString();
  private static final Instant NOW = FixedClockConfiguration.NOW;

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  private UUID hub;
  private UUID vehicle;

  @BeforeEach
  void aHubAndABike() {
    when(jwtDecoder.decode(RITA)).thenReturn(jwt(RITA, RITA_SUBJECT));
    when(jwtDecoder.decode(TOMAS)).thenReturn(jwt(TOMAS, TOMAS_SUBJECT));
    hub = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Marés Centro', 'HOTEL', ?::uuid, 40.6443, -8.6455, 12)
        """,
        hub,
        MARES_CENTRO);
    vehicle = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, 'EB-301', 'E_BIKE', 'AVAILABLE', ?, 80)
        """,
        vehicle,
        hub);
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

  private static Jwt jwt(String token, String subject) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject(subject)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", Map.of("roles", List.of("GUEST")))
        .build();
  }

  // A guest with an active stay and an active pass; returns the guest and the pass.
  private UUID[] guestWithPass(String subject, String stayCode, String email) {
    var stay = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Synthetic Guest', ?, date '2026-11-28', date '2026-12-02', 'ACTIVE')
        """,
        stay,
        MARES_CENTRO,
        stayCode);
    var guest = UUID.randomUUID();
    jdbc.update(
        "insert into guest (id, keycloak_id, stay_id, email, locale) values (?, ?, ?, ?, 'pt')",
        guest,
        subject,
        stay,
        email);
    var pass = UUID.randomUUID();
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents,
                          deposit_status, activated_at, expires_at, stay_ends_at)
        values (?, ?, 'STAY', 'ACTIVE', 2500, 5000, 'HELD', ?, timestamptz '2026-12-03 00:00:00+00',
                timestamptz '2026-12-03 00:00:00+00')
        """,
        pass,
        guest,
        Timestamp.from(NOW.minusSeconds(7200)));
    return new UUID[] {guest, pass};
  }

  // An ended trip if endedAt is given, otherwise one in use; each with its own used reservation.
  private UUID trip(UUID guest, UUID pass, Instant requestedAt, Instant endedAt) {
    var reservation = UUID.randomUUID();
    jdbc.update(
        """
        insert into reservation (id, pass_id, vehicle_id, vehicle_code, hub_id, status, created_at,
                                 expires_at)
        values (?, ?, ?, 'EB-301', ?, 'USED', ?, ?)
        """,
        reservation,
        pass,
        vehicle,
        hub,
        Timestamp.from(requestedAt),
        Timestamp.from(requestedAt.plusSeconds(900)));
    var trip = UUID.randomUUID();
    jdbc.update(
        """
        insert into trip (id, guest_id, pass_id, reservation_id, vehicle_id, vehicle_code, hub_id,
                          status, requested_at, started_at, ended_at, end_hub_id)
        values (?, ?, ?, ?, ?, 'EB-301', ?, ?, ?, ?, ?, ?)
        """,
        trip,
        guest,
        pass,
        reservation,
        vehicle,
        hub,
        endedAt == null ? "IN_USE" : "ENDED",
        Timestamp.from(requestedAt),
        Timestamp.from(requestedAt.plusSeconds(5)),
        endedAt == null ? null : Timestamp.from(endedAt),
        endedAt == null ? null : hub);
    return trip;
  }

  @Requirement("RIA-107")
  @Test
  void shouldListTheGuestsTripsNewestFirst() throws Exception {
    var rita = guestWithPass(RITA_SUBJECT, "LTAA2345", "rita.almeida@example.test");
    var past = trip(rita[0], rita[1], NOW.minusSeconds(7200), NOW.minusSeconds(3600));
    var active = trip(rita[0], rita[1], NOW.minusSeconds(600), null);

    mvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, "Bearer " + RITA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(active.toString()))
        .andExpect(jsonPath("$[0].status").value("IN_USE"))
        .andExpect(jsonPath("$[0].endedAt").doesNotExist())
        .andExpect(jsonPath("$[1].id").value(past.toString()))
        .andExpect(jsonPath("$[1].status").value("ENDED"))
        .andExpect(jsonPath("$[1].endHubId").value(hub.toString()));
  }

  @Requirement("RIA-107")
  @Test
  void shouldNeverListAnotherGuestsTrips() throws Exception {
    var rita = guestWithPass(RITA_SUBJECT, "LTAA2345", "rita.almeida@example.test");
    trip(rita[0], rita[1], NOW.minusSeconds(600), null);
    guestWithPass(TOMAS_SUBJECT, "LTAB2345", "tomas.ferreira@example.test");

    mvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOMAS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
