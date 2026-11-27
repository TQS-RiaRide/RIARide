package com.riaride.trips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import com.riaride.FixedClockConfiguration;
import com.riaride.MqttContracts;
import com.riaride.TestMosquitto;
import com.riaride.TestMosquitto.CommandRecorder;
import com.riaride.TestMosquitto.RecordedCommand;
import com.riaride.TestRows;
import com.riaride.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Integration test (Failsafe): a whole trip through the API with the real MQTT adapter, PostgreSQL
// and Mosquitto in Testcontainers (RIA-54, RIA-55). Starting the trip publishes UNLOCK to the bike,
// the UNLOCKED reading the test sends back as the simulator would confirms it, and ending it
// publishes LOCK with the hub. The adapter alone is covered by MqttVehicleCommandsIT; here nothing
// between the controller and the broker is replaced. Only the JwtDecoder is, to choose the caller.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class TripCommandsOverMqttIT {

  private static final Duration MQTT_DELIVERY = Duration.ofSeconds(5);
  private static final String STAY_CODE = "MQTT2345";
  private static final String GUEST = "mqtt-guest-token";
  private static final String GUEST_SUBJECT = "9d4b0c6e-7f5a-4b1c-8d3e-4f5a6b7c8d93";
  private static final String BIKE = "EB-901";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  private UUID startHub;
  private UUID endHub;

  @DynamicPropertySource
  static void mqtt(DynamicPropertyRegistry registry) {
    TestMosquitto.registerProperties(registry);
  }

  @BeforeEach
  void guestWithAPassAndABike() {
    when(jwtDecoder.decode(GUEST)).thenReturn(guestToken());
    var guestId = TestRows.guest(jdbc, STAY_CODE, "joana.matos@example.test", GUEST_SUBJECT);
    TestRows.pass(jdbc, guestId, "INACTIVE");
    startHub = TestRows.touristSpot(jdbc, "Ponte de Carcavelos", 4);
    endHub = TestRows.touristSpot(jdbc, "Fábrica Centro de Ciência Viva", 4);
    TestRows.vehicle(jdbc, BIKE, "AVAILABLE", startHub);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from trip where vehicle_code = ?", BIKE);
    jdbc.update("delete from reservation where vehicle_code = ?", BIKE);
    jdbc.update("delete from vehicle where code = ?", BIKE);
    jdbc.update("delete from hub where id in (?, ?)", startHub, endHub);
    jdbc.update(
        "delete from pass where guest_id in (select id from guest where keycloak_id = ?)",
        GUEST_SUBJECT);
    jdbc.update("delete from guest where keycloak_id = ?", GUEST_SUBJECT);
    jdbc.update("delete from stay where stay_code = ?", STAY_CODE);
  }

  private static Jwt guestToken() {
    return Jwt.withTokenValue(GUEST)
        .header("alg", "RS256")
        .subject(GUEST_SUBJECT)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", Map.of("roles", List.of("GUEST")))
        .build();
  }

  @Requirement({"RIA-54", "RIA-55"})
  @Test
  void shouldUnlockTheBikeOverMqttAndLockItAtTheEndHub() throws Exception {
    try (var recorder = TestMosquitto.recordCommands()) {
      var tripId = startTrip();

      var unlock = awaitCommand(recorder, "UNLOCK");
      assertThat(MqttContracts.commandViolations(unlock.payload())).isEmpty();
      assertThat(tripStatus(tripId)).isEqualTo("STARTING");

      TestMosquitto.publishTelemetry(
          BIKE, 80, null, "UNLOCKED", FixedClockConfiguration.NOW.plusSeconds(5));
      await().atMost(MQTT_DELIVERY).until(() -> "IN_USE".equals(tripStatus(tripId)));

      mvc.perform(
              post("/api/v1/trips/{id}/end", tripId)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + GUEST)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"hubId\": \"" + endHub + "\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ENDED"));

      var lock = awaitCommand(recorder, "LOCK");
      assertThat(MqttContracts.commandViolations(lock.payload())).isEmpty();
      assertThat(JsonPath.<String>read(lock.payload(), "$.hubId")).isEqualTo(endHub.toString());
    }
  }

  private String startTrip() throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/trips")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + GUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"vehicleCode\": \"" + BIKE + "\"}"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private static RecordedCommand awaitCommand(CommandRecorder recorder, String command) {
    return await()
        .atMost(MQTT_DELIVERY)
        .until(
            () ->
                recorder.commands().stream()
                    .filter(sent -> BIKE.equals(sent.vehicleCode()))
                    .filter(sent -> command.equals(sent.command()))
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private String tripStatus(String tripId) {
    return jdbc.queryForObject("select status from trip where id = ?::uuid", String.class, tripId);
  }
}
