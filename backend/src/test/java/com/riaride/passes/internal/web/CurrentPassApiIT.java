package com.riaride.passes.internal.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

// Integration test (Failsafe): GET /api/v1/passes/current (RIA-107) through the real security
// filter chain, with PostgreSQL in Testcontainers. A guest reads their open pass, with its
// activation, expiry and the end of the stay; a closed pass is not current (404), and another
// guest's pass is never returned.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class CurrentPassApiIT {

  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final String RITA = "rita-token";
  private static final String TOMAS = "tomas-token";
  private static final String RITA_SUBJECT = UUID.randomUUID().toString();
  private static final String TOMAS_SUBJECT = UUID.randomUUID().toString();
  private static final Instant ACTIVATED = Instant.parse("2026-11-30T09:00:00Z");
  private static final Instant EXPIRES = Instant.parse("2026-12-01T09:00:00Z");
  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private JwtDecoder jwtDecoder;

  private UUID ritasGuest;

  @BeforeEach
  void twoGuests() {
    when(jwtDecoder.decode(RITA)).thenReturn(jwt(RITA, RITA_SUBJECT));
    when(jwtDecoder.decode(TOMAS)).thenReturn(jwt(TOMAS, TOMAS_SUBJECT));
    ritasGuest = guest(RITA_SUBJECT, "CPAA2345", "rita.almeida@example.test");
    guest(TOMAS_SUBJECT, "CPAB2345", "tomas.ferreira@example.test");
  }

  @AfterEach
  void cleanUp() {
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

  private UUID guest(String subject, String stayCode, String email) {
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
    return guest;
  }

  private void pass(UUID guest, String status, Instant activatedAt, Instant expiresAt) {
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents,
                          deposit_status, activated_at, expires_at, stay_ends_at)
        values (?, ?, 'ONE_DAY', ?, 900, 5000, 'HELD', ?, ?, ?)
        """,
        UUID.randomUUID(),
        guest,
        status,
        activatedAt == null ? null : Timestamp.from(activatedAt),
        expiresAt == null ? null : Timestamp.from(expiresAt),
        Timestamp.from(STAY_ENDS));
  }

  @Requirement("RIA-107")
  @Test
  void shouldGiveTheGuestTheirActivePass() throws Exception {
    pass(ritasGuest, "ACTIVE", ACTIVATED, EXPIRES);

    mvc.perform(get("/api/v1/passes/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + RITA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passType").value("ONE_DAY"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.priceCents").value(900))
        .andExpect(jsonPath("$.depositCents").value(5000))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.activatedAt").value(ACTIVATED.toString()))
        .andExpect(jsonPath("$.expiresAt").value(EXPIRES.toString()))
        .andExpect(jsonPath("$.stayEndsAt").value(STAY_ENDS.toString()));
  }

  // BR4: a paid pass is not active until the first trip, so it has no activation or expiry yet.
  @Requirement("RIA-107")
  @Test
  void shouldGiveAPaidPassThatWasNeverUsed() throws Exception {
    pass(ritasGuest, "INACTIVE", null, null);

    mvc.perform(get("/api/v1/passes/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + RITA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.activatedAt").doesNotExist())
        .andExpect(jsonPath("$.expiresAt").doesNotExist());
  }

  @Requirement("RIA-107")
  @Test
  void shouldAnswer404WhenTheGuestsOnlyPassIsClosed() throws Exception {
    pass(ritasGuest, "CLOSED", ACTIVATED, EXPIRES);

    mvc.perform(get("/api/v1/passes/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + RITA))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.title").value("No current pass"));
  }

  @Requirement("RIA-107")
  @Test
  void shouldNeverGiveAnotherGuestsPass() throws Exception {
    pass(ritasGuest, "ACTIVE", ACTIVATED, EXPIRES);

    mvc.perform(get("/api/v1/passes/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOMAS))
        .andExpect(status().isNotFound());
  }
}
