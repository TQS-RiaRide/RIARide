package com.riaride.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestMosquitto;
import com.riaride.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Integration test (Failsafe): telemetry the platform must not use, published to Mosquitto (in
// Testcontainers) as a faulty simulator could send it, goes through the MQTT adapter and the fleet
// listener without changing the bike's battery, and the subscriber keeps processing what follows.
// A valid reading for another bike, published afterwards on the same broker, marks the point by
// which the unusable message was processed: no sleeps.
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class UnusableTelemetryIT {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000b103");
  private static final Duration TELEMETRY_LAG = Duration.ofSeconds(5);
  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final String TOPIC = "riaride/vehicles/EB-801/telemetry";

  @Autowired private JdbcTemplate jdbc;

  @DynamicPropertySource
  static void mqtt(DynamicPropertyRegistry registry) {
    TestMosquitto.registerProperties(registry);
  }

  // EB-801 last reported 80 % at NOW; EB-802 is the marker bike.
  @BeforeEach
  void twoBikesAtAHub() {
    removeBikesAndHub();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Farol da Barra', 'TOURIST_SPOT', null, 40.64, -8.75, 6)
        """,
        HUB);
    for (var code : new String[] {"EB-801", "EB-802"}) {
      jdbc.update(
          """
          insert into vehicle (id, code, type, status, hub_id, battery_pct, last_telemetry_at)
          values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80, ?)
          """,
          UUID.randomUUID(),
          code,
          HUB,
          Timestamp.from(NOW));
    }
  }

  @AfterEach
  void removeBikesAndHub() {
    jdbc.update("delete from vehicle where hub_id = ?", HUB);
    jdbc.update("delete from hub where id = ?", HUB);
  }

  static Stream<Arguments> unusableReadings() {
    var valid = NOW.plusSeconds(5);
    return Stream.of(
        Arguments.of("not JSON", "{\"batteryPct\": 5,"),
        Arguments.of("no lock state", reading(valid, "5", null)),
        Arguments.of(
            "no message id", "{\"emittedAt\": \"%s\", \"batteryPct\": 5}".formatted(valid)),
        Arguments.of("battery above 100 %", reading(valid, "101", "LOCKED")),
        Arguments.of("battery below 0 %", reading(valid, "-1", "LOCKED")),
        Arguments.of("battery as text", reading(valid, "\"5\"", "LOCKED")),
        Arguments.of("timestamp that is not a date", reading("yesterday", "5", "LOCKED")),
        // BR12: a late or redelivered reading never overwrites a newer one.
        Arguments.of("older than the last reading", reading(NOW.minusSeconds(60), "5", "LOCKED")),
        // The simulator's clock is more than a minute ahead of the platform's.
        Arguments.of("from the future", reading(NOW.plus(Duration.ofHours(1)), "5", "LOCKED")));
  }

  @Requirement("RIA-59")
  @ParameterizedTest(name = "{0}")
  @MethodSource("unusableReadings")
  void shouldKeepTheLastBatteryWhenTelemetryIsUnusable(String reason, String payload) {
    TestMosquitto.publish(TOPIC, payload);
    TestMosquitto.publishTelemetry("EB-802", 42, HUB.toString());

    await().atMost(TELEMETRY_LAG).until(() -> battery("EB-802") == 42);
    assertThat(battery("EB-801")).as(reason).isEqualTo(80);
  }

  // The simulator's demo fleet also reports bikes no operator has registered (RIA-57).
  @Requirement("RIA-57")
  @Test
  void shouldIgnoreTelemetryForABikeThatIsNotRegistered() {
    TestMosquitto.publishTelemetry("EB-999", 5, HUB.toString());
    TestMosquitto.publishTelemetry("EB-802", 42, HUB.toString());

    await().atMost(TELEMETRY_LAG).until(() -> battery("EB-802") == 42);
    assertThat(
            jdbc.queryForObject("select count(*) from vehicle where hub_id = ?", Long.class, HUB))
        .isEqualTo(2);
    assertThat(battery("EB-801")).isEqualTo(80);
  }

  private static String reading(Object emittedAt, String batteryPct, String lockState) {
    return """
    {"messageId": "%s", "emittedAt": "%s", "batteryPct": %s%s}
    """
        .formatted(
            UUID.randomUUID(),
            emittedAt,
            batteryPct,
            lockState == null ? "" : ", \"lockState\": \"" + lockState + "\"");
  }

  private int battery(String code) {
    return jdbc.queryForObject(
        "select battery_pct from vehicle where code = ?", Integer.class, code);
  }
}
