package com.riaride.accounts.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Integration test (Failsafe): GET /api/v1/me (RIA-86) through the real security filter chain.
// The web app calls it after login to learn who the user is and which workspaces to show in the
// menu. Only the JwtDecoder is replaced, so the tests choose the claims of the token; the token
// validation against Keycloak itself is covered by RoleAccessMatrixIT.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class CurrentUserApiIT {

  private static final String ME = "/api/v1/me";
  private static final String TOKEN = "access-token";
  private static final String SUBJECT = "6f1c2b9e-4a57-4d0e-9a51-2f0d8c3b7e10";

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  private static Jwt token(List<String> realmRoles) {
    return Jwt.withTokenValue(TOKEN)
        .header("alg", "RS256")
        .subject(SUBJECT)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("name", "Rui Almeida")
        .claim("email", "rui.almeida@example.test")
        .claim("realm_access", Map.of("roles", realmRoles))
        .build();
  }

  private ResultActions getMe(List<String> realmRoles) throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenReturn(token(realmRoles));
    return mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));
  }

  // Scenario "An operator sees the operator dashboard and not the hotel backoffice".
  @Requirement("RIA-86")
  @Test
  void shouldReturnOperatorWithOperatorDashboardAndTicketsButNotHotelBackoffice() throws Exception {
    getMe(List.of("default-roles-riaride", "offline_access", "uma_authorization", "OPERATOR"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(SUBJECT))
        .andExpect(jsonPath("$.name").value("Rui Almeida"))
        .andExpect(jsonPath("$.email").value("rui.almeida@example.test"))
        .andExpect(jsonPath("$.roles", contains("OPERATOR")))
        .andExpect(jsonPath("$.workspaces", contains("OPERATOR_DASHBOARD", "MAINTENANCE_TICKETS")));
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{0} sees {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "GUEST         | GUEST_DASHBOARD",
        "TECHNICIAN    | MAINTENANCE_TICKETS",
        "HOTEL_MANAGER | HOTEL_BACKOFFICE",
        "ADMIN         | OPERATOR_DASHBOARD,MAINTENANCE_TICKETS,HOTEL_BACKOFFICE,"
            + "ACCOUNT_ADMINISTRATION"
      })
  void shouldReturnTheWorkspacesOfEachRole(String role, String workspaces) throws Exception {
    getMe(List.of(role))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", contains(role)))
        .andExpect(jsonPath("$.workspaces", contains(workspaces.split(","))));
  }

  @Requirement("RIA-86")
  @Test
  void shouldReturnNoRolesAndNoWorkspacesForAUserWithoutAPlatformRole() throws Exception {
    getMe(List.of("default-roles-riaride", "offline_access"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", empty()))
        .andExpect(jsonPath("$.workspaces", empty()));
  }

  @Requirement("RIA-86")
  @Test
  void shouldRefuseARequestWithoutATokenWith401ProblemDetail() throws Exception {
    mvc.perform(get(ME))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401));
  }
}
