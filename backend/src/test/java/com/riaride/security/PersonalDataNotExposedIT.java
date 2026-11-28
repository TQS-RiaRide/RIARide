package com.riaride.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestKeycloak;
import com.riaride.TestKeycloak.TestUser;
import com.riaride.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

// Integration test (Failsafe): data protection (QA Manual 3.4, RIA-141). A guest's email,
// password and stay code, the guest name a hotel registers, and access tokens are personal or
// secret data: they never appear in the logs, and an error response never repeats them. Each test
// drives the real flow with synthetic data, PostgreSQL and Keycloak in Testcontainers and the real
// security filter chain, through every outcome it can reach, and then searches everything the
// application wrote to the console and every error response for those values.
//
// Out of reach here: Keycloak being unavailable during sign-up (its handler logs a fixed message
// and the HTTP status, see GuestProblemHandler), and the login form itself, which Keycloak serves.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class PersonalDataNotExposedIT {

  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

  // Synthetic values, chosen so that they cannot appear in a log by chance.
  private static final String EMAIL = "leonor.vieira.gdpr@example.test";
  private static final String MALFORMED_EMAIL = "leonor.vieira.gdpr.example.test";
  private static final String PASSWORD = "Mare-Alta-Gdpr-2026";
  private static final String SHORT_PASSWORD = "Gdpr-26";
  private static final String STAY_CODE = "H5N7R3KQ";
  private static final String ENDED_STAY_CODE = "J6P8S4LV";
  private static final String UNKNOWN_STAY_CODE = "W9X2Y3Z4";
  private static final String GUEST_NAME = "Leonor Brandão Vieira";

  @DynamicPropertySource
  static void keycloakProperties(DynamicPropertyRegistry registry) {
    TestKeycloak.registerProperties(registry);
  }

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private final List<String> keycloakUsers = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from guest");
    jdbc.update("delete from stay");
    TestKeycloak.deleteUsersWithEmail(EMAIL);
    keycloakUsers.forEach(TestKeycloak::deleteUser);
  }

  private TestUser logInWithRole(String role) {
    var user = TestKeycloak.logInWithRole(role);
    keycloakUsers.add(user.id());
    return user;
  }

  private void givenStay(String code, String checkIn, String checkOut) {
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?::uuid, ?::uuid, ?, ?, ?::date, ?::date, 'ACTIVE')
        """,
        UUID.randomUUID().toString(),
        MARES_CENTRO,
        GUEST_NAME,
        code,
        checkIn,
        checkOut);
  }

  private static String signUpBody(String email, String password, String stayCode) {
    return """
    {"email": "%s", "password": "%s", "stayCode": "%s", "locale": "pt"}
    """
        .formatted(email, password, stayCode);
  }

  private static String stayBody(String guestName, String checkIn, String checkOut) {
    return """
    {"hotelId": "%s", "guestName": "%s", "checkIn": "%s", "checkOut": "%s"}
    """
        .formatted(MARES_CENTRO, guestName, checkIn, checkOut);
  }

  // The body of an error response, after checking its status.
  private String errorBody(ResultActions request, ResultMatcher expected) throws Exception {
    return request.andExpect(expected).andReturn().getResponse().getContentAsString();
  }

  private ResultActions signUp(String json) throws Exception {
    return mvc.perform(
        post("/api/v1/guests").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions registerStay(String token, String json) throws Exception {
    return mvc.perform(
        post("/api/v1/stays")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions me(String token) throws Exception {
    return mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  // Sign-up through each of its outcomes: created, then refused because the email already has an
  // account, the stay code is unknown or its stay has ended, the email or the password is invalid,
  // or the body is not JSON.
  @Requirement("RIA-51")
  @Test
  void shouldNeverLogOrReturnTheEmailPasswordOrStayCodeOfASignUp(CapturedOutput output)
      throws Exception {
    givenStay(STAY_CODE, "2026-11-28", "2026-12-02");
    givenStay(ENDED_STAY_CODE, "2026-11-20", "2026-11-25");

    signUp(signUpBody(EMAIL, PASSWORD, STAY_CODE)).andExpect(status().isCreated());
    var errors =
        List.of(
            errorBody(signUp(signUpBody(EMAIL, PASSWORD, STAY_CODE)), status().is4xxClientError()),
            errorBody(
                signUp(signUpBody(EMAIL, PASSWORD, UNKNOWN_STAY_CODE)),
                status().isUnprocessableContent()),
            errorBody(
                signUp(signUpBody(EMAIL, PASSWORD, ENDED_STAY_CODE)),
                status().isUnprocessableContent()),
            errorBody(
                signUp(signUpBody(MALFORMED_EMAIL, PASSWORD, STAY_CODE)), status().isBadRequest()),
            errorBody(
                signUp(signUpBody(EMAIL, SHORT_PASSWORD, STAY_CODE)), status().isBadRequest()),
            errorBody(
                signUp(signUpBody(EMAIL, PASSWORD, STAY_CODE).replace("}", ",")),
                status().isBadRequest()));

    var values =
        List.of(
            EMAIL,
            MALFORMED_EMAIL,
            PASSWORD,
            SHORT_PASSWORD,
            STAY_CODE,
            ENDED_STAY_CODE,
            UNKNOWN_STAY_CODE);
    assertThat(errors).allSatisfy(body -> assertThat(body).doesNotContain(values));
    assertThat(output.getAll()).doesNotContain(values);
  }

  // A hotel registers a stay: the response gives the stay code to the hotel, but the logs never
  // hold it or the guest's name, and a refused registration does not repeat the name.
  @Requirement("RIA-50")
  @Test
  void shouldNeverLogTheGuestNameOrStayCodeOfAStayRegistration(CapturedOutput output)
      throws Exception {
    var admin = logInWithRole("ADMIN");

    var created =
        registerStay(admin.accessToken(), stayBody(GUEST_NAME, "2026-12-01", "2026-12-05"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String stayCode = JsonPath.read(created, "$.stayCode");
    var errors =
        List.of(
            errorBody(
                registerStay(admin.accessToken(), stayBody(GUEST_NAME, "2026-12-05", "2026-12-01")),
                status().isUnprocessableContent()),
            errorBody(
                registerStay(
                    admin.accessToken(),
                    stayBody(GUEST_NAME + "\\u202E", "2026-12-01", "2026-12-05")),
                status().isBadRequest()),
            errorBody(
                registerStay(
                    admin.accessToken(),
                    stayBody(GUEST_NAME, "2026-12-01", "2026-12-05").replace("}", ",")),
                status().isBadRequest()));

    assertThat(errors).allSatisfy(body -> assertThat(body).doesNotContain(GUEST_NAME));
    assertThat(output.getAll()).doesNotContain(GUEST_NAME, stayCode);
  }

  // Login is Keycloak's; the backend only checks the access token on each request. A valid token,
  // a tampered one and one used where its role is not allowed never reach the logs, and the 401
  // and 403 problem details do not repeat them.
  @Requirement("RIA-86")
  @Test
  void shouldNeverLogOrReturnAnAccessToken(CapturedOutput output) throws Exception {
    var guest = logInWithRole("GUEST");
    var tampered = guest.accessToken().substring(0, guest.accessToken().length() - 4) + "AAAA";

    me(guest.accessToken()).andExpect(status().isOk());
    var errors =
        List.of(
            errorBody(me(tampered), status().isUnauthorized()),
            errorBody(
                registerStay(guest.accessToken(), stayBody(GUEST_NAME, "2026-12-01", "2026-12-05")),
                status().isForbidden()));

    var signature = guest.accessToken().substring(guest.accessToken().lastIndexOf('.') + 1);
    assertThat(errors)
        .allSatisfy(body -> assertThat(body).doesNotContain(guest.accessToken(), tampered));
    assertThat(output.getAll()).doesNotContain(guest.accessToken(), tampered, signature);
  }
}
