package com.riaride.trips.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

// Integration test (Failsafe): POST /api/v1/reservations and GET /api/v1/reservations/{id} through
// the real security filter chain, with PostgreSQL in Testcontainers (RIA-90, BR11). Only the
// JwtDecoder and the MQTT commands are replaced. A booking holds the bike for 15 minutes, a trip
// started on the same bike uses it, and a pass holds one bike at a time (BR6), also when two
// bookings run at the same moment. Today is fixed at 30 November 2026, 10:00 in Vila Ria.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class BookBikeApiIT {

  private static final String RESERVATIONS = "/api/v1/reservations";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final String RITA = "rita-token";
  private static final String TOMAS = "tomas-token";
  private static final String NO_PASS = "no-pass-token";
  private static final String OPERATOR = "operator-token";
  private static final String RITA_SUBJECT = "6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60";
  private static final String TOMAS_SUBJECT = "7b2f8a4c-5d3e-4f9a-8b1c-2d3e4f5a6b71";
  private static final String NO_PASS_SUBJECT = "8c3a9b5d-6e4f-4a0b-9c2d-3e4f5a6b7c82";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private VehicleCommands vehicleCommands;

  private UUID hubId;

  @BeforeEach
  void guestsAndBikes() {
    when(jwtDecoder.decode(RITA)).thenReturn(jwt(RITA, RITA_SUBJECT, "GUEST"));
    when(jwtDecoder.decode(TOMAS)).thenReturn(jwt(TOMAS, TOMAS_SUBJECT, "GUEST"));
    when(jwtDecoder.decode(NO_PASS)).thenReturn(jwt(NO_PASS, NO_PASS_SUBJECT, "GUEST"));
    when(jwtDecoder.decode(OPERATOR))
        .thenReturn(jwt(OPERATOR, UUID.randomUUID().toString(), "OPERATOR"));
    givenPass(givenGuest(RITA_SUBJECT, "BKPA2345", "rita.almeida@example.test"));
    givenPass(givenGuest(TOMAS_SUBJECT, "BKPB2345", "tomas.ferreira@example.test"));
    givenGuest(NO_PASS_SUBJECT, "BKPC2345", "ines.costa@example.test");
    hubId = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Marés Centro', 'HOTEL', ?::uuid, 40.6405, -8.6538, 10)
        """,
        hubId,
        MARES_CENTRO);
    givenBike("EB-701", 80);
    givenBike("EB-702", 80);
    givenBike("EB-703", 19);
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

  // A stay that is active on the fixed test date, and the guest account linked to it.
  private UUID givenGuest(String keycloakId, String stayCode, String email) {
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
    return guestId;
  }

  // A paid stay pass, not active until the first trip (BR4).
  private void givenPass(UUID guestId) {
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
        values (?, ?, 'STAY', 'INACTIVE', 4000, 5000, 'HELD')
        """,
        UUID.randomUUID(),
        guestId);
  }

  private void givenBike(String code, int batteryPct) {
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', 'AVAILABLE', ?, ?)
        """,
        UUID.randomUUID(),
        code,
        hubId,
        batteryPct);
  }

  private ResultActions book(String token, String vehicleCode) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(RESERVATIONS)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleCode\": \"" + vehicleCode + "\"}"));
  }

  private ResultActions startTrip(String token, String vehicleCode) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/trips")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleCode\": \"" + vehicleCode + "\"}"));
  }

  private ResultActions read(String token, String location) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.get(location).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private String bikeStatus(String code) {
    return jdbc.queryForObject("select status from vehicle where code = ?", String.class, code);
  }

  private int activeBookings() {
    return jdbc.queryForObject(
        "select count(*) from reservation where status = 'ACTIVE'", Integer.class);
  }

  @Requirement("RIA-90")
  @Test
  void shouldBookTheBikeAndHoldItFor15Minutes() throws Exception {
    book(RITA, "EB-701")
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION,
                    matchesPattern("http://localhost/api/v1/reservations/[0-9a-f-]{36}")))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.vehicleCode").value("EB-701"))
        .andExpect(jsonPath("$.hubId").value(hubId.toString()))
        .andExpect(jsonPath("$.createdAt").value("2026-11-30T10:00:00Z"))
        .andExpect(jsonPath("$.expiresAt").value("2026-11-30T10:15:00Z"));

    assertThat(bikeStatus("EB-701")).isEqualTo("RESERVED");
    assertThat(
            jdbc.queryForObject(
                "select status from pass p join guest g on g.id = p.guest_id"
                    + " where g.keycloak_id = ?",
                String.class,
                RITA_SUBJECT))
        .isEqualTo("INACTIVE");
  }

  @Requirement("RIA-90")
  @Test
  void shouldReadTheGuestsOwnBookingAndHideItFromOthers() throws Exception {
    var location = book(RITA, "EB-701").andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

    read(RITA, location)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.vehicleCode").value("EB-701"));
    read(TOMAS, location)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Booking not found"));
  }

  // A trip started on the booked bike uses the booking instead of reserving the bike again.
  @Requirement("RIA-90")
  @Test
  void shouldStartATripOnTheBookedBikeFromTheBooking() throws Exception {
    book(RITA, "EB-701").andExpect(status().isCreated());

    startTrip(RITA, "EB-701").andExpect(status().isAccepted());

    assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from trip t join reservation r on r.id = t.reservation_id"
                    + " where r.vehicle_code = 'EB-701'",
                Integer.class))
        .isEqualTo(1);
  }

  @Requirement("RIA-90")
  @Test
  void shouldRefuseATripOnAnotherBikeWhileTheBookingIsHeld() throws Exception {
    book(RITA, "EB-701").andExpect(status().isCreated());

    startTrip(RITA, "EB-702")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Reservation held for another bike"));

    assertThat(bikeStatus("EB-702")).isEqualTo("AVAILABLE");
  }

  @Requirement("RIA-90")
  @Test
  void shouldRefuseASecondBookingWhileOneIsHeld() throws Exception {
    book(RITA, "EB-701").andExpect(status().isCreated());

    book(RITA, "EB-702")
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Booking already held"));

    assertThat(bikeStatus("EB-702")).isEqualTo("AVAILABLE");
    assertThat(activeBookings()).isEqualTo(1);
  }

  // BR11: once the hold ended, the pass can book another bike, and the first one is available.
  @Requirement("RIA-90")
  @Test
  void shouldReleaseAnEndedBookingAndBookAnotherBike() throws Exception {
    book(RITA, "EB-701").andExpect(status().isCreated());
    jdbc.update("update reservation set expires_at = ?::timestamptz", "2026-11-30T10:00:00Z");

    book(RITA, "EB-702").andExpect(status().isCreated());

    assertThat(bikeStatus("EB-701")).isEqualTo("AVAILABLE");
    assertThat(bikeStatus("EB-702")).isEqualTo("RESERVED");
    assertThat(jdbc.queryForList("select status from reservation", String.class))
        .containsExactlyInAnyOrder("EXPIRED", "ACTIVE");
  }

  // BR6 under concurrency: two bookings for the same pass on two bikes at the same moment.
  @Requirement("RIA-90")
  @Test
  void onlyOneOfTwoParallelBookingsForSamePassSucceeds() throws Exception {
    var statuses = bookTogether(RITA, "EB-701", RITA, "EB-702");

    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(activeBookings()).isEqualTo(1);
    assertThat(List.of(bikeStatus("EB-701"), bikeStatus("EB-702")))
        .containsExactlyInAnyOrder("RESERVED", "AVAILABLE");
  }

  @Requirement("RIA-90")
  @Test
  void onlyOneOfTwoGuestsBooksTheSameBike() throws Exception {
    var statuses = bookTogether(RITA, "EB-701", TOMAS, "EB-701");

    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(activeBookings()).isEqualTo(1);
    assertThat(bikeStatus("EB-701")).isEqualTo("RESERVED");
  }

  private List<Integer> bookTogether(
      String firstToken, String firstBike, String secondToken, String secondBike) throws Exception {
    var go = new CountDownLatch(1);
    try (var threads = Executors.newFixedThreadPool(2)) {
      var first =
          threads.submit(() -> {
            go.await();
            return book(firstToken, firstBike).andReturn().getResponse().getStatus();
          });
      var second =
          threads.submit(() -> {
            go.await();
            return book(secondToken, secondBike).andReturn().getResponse().getStatus();
          });
      go.countDown();
      return List.of(first.get(), second.get());
    }
  }

  // BR12: a bike below 20 % battery cannot be booked.
  @Requirement("RIA-90")
  @Test
  void shouldRefuseABikeWithLowBattery() throws Exception {
    book(RITA, "EB-703")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Bike not available"));

    assertThat(bikeStatus("EB-703")).isEqualTo("AVAILABLE");
  }

  @Requirement("RIA-90")
  @Test
  void shouldReturn404ProblemForUnknownBike() throws Exception {
    book(RITA, "EB-999")
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Bike not found"));
  }

  @Requirement("RIA-90")
  @Test
  void shouldReturn403ProblemWithoutValidPass() throws Exception {
    book(NO_PASS, "EB-701")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("No valid pass"));

    assertThat(bikeStatus("EB-701")).isEqualTo("AVAILABLE");
  }

  @Requirement("RIA-90")
  @Test
  void shouldRefuseOperatorWith403() throws Exception {
    book(OPERATOR, "EB-701")
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(bikeStatus("EB-701")).isEqualTo("AVAILABLE");
  }

  @Requirement("RIA-90")
  @Test
  void shouldReturn400ProblemForABlankBikeCode() throws Exception {
    book(RITA, " ")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }
}
