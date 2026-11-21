package com.riaride.fleet;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestMosquitto;
import com.riaride.TestcontainersConfiguration;
import java.time.Duration;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Integration test (Failsafe), RIA-59: a low-battery telemetry message published to Mosquitto (in
// Testcontainers) goes through the MQTT adapter, the telemetry event and the fleet listener into
// PostgreSQL, and the bike stops being bookable while its status stays "available". Waits use
// Awaitility, never sleeps. The 5 s bound is the telemetry lag of RIA-58.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class LowBatteryBookingIT {

	private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000b059");
	private static final Duration TELEMETRY_LAG = Duration.ofSeconds(5);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@DynamicPropertySource
	static void mqtt(DynamicPropertyRegistry registry) {
		TestMosquitto.registerProperties(registry);
	}

	@BeforeEach
	void seedAvailableBikeWith80PercentBattery() {
		removeVehiclesAndHub();
		jdbc.update("""
			insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
			values (?, 'Cais dos Moliceiros', 'TOURIST_SPOT', null, 40.64, -8.65, 6)
			""", HUB);
		jdbc.update("""
			insert into vehicle (id, code, type, status, hub_id, battery_pct)
			values (?, 'EB-601', 'E_BIKE', 'AVAILABLE', ?, 80)
			""", UUID.randomUUID(), HUB);
		when(jwtDecoder.decode("GUEST")).thenReturn(Jwt.withTokenValue("GUEST")
			.header("alg", "RS256")
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("realm_access", Map.of("roles", List.of("GUEST")))
			.build());
	}

	@AfterEach
	void removeVehiclesAndHub() {
		jdbc.update("delete from vehicle");
		jdbc.update("delete from hub where id = ?", HUB);
	}

	@Requirement("RIA-59")
	@Test
	void shouldStopOfferingABikeWhenTelemetryReports18PercentBattery() {
		TestMosquitto.publishTelemetry("EB-601", 18, HUB.toString());

		await().atMost(TELEMETRY_LAG).untilAsserted(() -> mvc
			.perform(get("/api/v1/hubs/{hubId}/vehicles", HUB).header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].code").value("EB-601"))
			.andExpect(jsonPath("$[0].batteryPct").value(18))
			.andExpect(jsonPath("$[0].status").value("AVAILABLE"))
			.andExpect(jsonPath("$[0].bookable").value(false)));
	}

	@Requirement("RIA-59")
	@Test
	void shouldOfferTheBikeAgainWhenItIsChargedBackTo20Percent() {
		TestMosquitto.publishTelemetry("EB-601", 18, HUB.toString());
		await().atMost(TELEMETRY_LAG).until(() -> batteryOf("EB-601") == 18);

		TestMosquitto.publishTelemetry("EB-601", 20, HUB.toString());

		await().atMost(TELEMETRY_LAG).untilAsserted(() -> mvc
			.perform(get("/api/v1/hubs/{hubId}/vehicles", HUB).header(HttpHeaders.AUTHORIZATION, "Bearer GUEST"))
			.andExpect(jsonPath("$[0].batteryPct").value(20))
			.andExpect(jsonPath("$[0].bookable").value(true)));
	}

	private int batteryOf(String code) {
		return jdbc.queryForObject("select battery_pct from vehicle where code = ?", Integer.class, code);
	}

}
