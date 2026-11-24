package com.riaride.accounts.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestKeycloak;
import com.riaride.TestcontainersConfiguration;
import com.riaride.accounts.internal.domain.Guest;
import com.riaride.accounts.internal.persistence.GuestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Integration test (Failsafe): POST /api/v1/guests through the real security filter chain, with
// PostgreSQL and Keycloak in Testcontainers. Today is fixed at 30 November 2026 in Vila Ria.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class SignUpGuestApiIT {

  private static final String GUESTS = "/api/v1/guests";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final String EMAIL = "rita.almeida@example.test";
  private static final String PASSWORD = "Sunny-Ria-2026";
  private static final String ACTIVE_CODE = "K7M3Q9TX";
  private static final String ENDED_CODE = "P4R8W2NZ";
  private static final String UNKNOWN_CODE = "ZZZZ2222";

  @DynamicPropertySource
  static void keycloakProperties(DynamicPropertyRegistry registry) {
    TestKeycloak.registerProperties(registry);
  }

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoSpyBean private GuestRepository guests;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from guest");
    jdbc.update("delete from stay");
    TestKeycloak.deleteUsersWithEmail(EMAIL);
    TestKeycloak.deleteUsersWithEmail("other.guest@example.test");
  }

  private String givenStay(String code, String checkIn, String checkOut) {
    return givenStay(code, checkIn, checkOut, "ACTIVE");
  }

  private String givenStay(String code, String checkIn, String checkOut, String status) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?::uuid, ?::uuid, 'Rita Almeida', ?, ?::date, ?::date, ?)
        """,
        id,
        MARES_CENTRO,
        code,
        checkIn,
        checkOut,
        status);
    return id;
  }

  private String givenActiveStay() {
    return givenStay(ACTIVE_CODE, "2026-11-28", "2026-12-02");
  }

  private static String body(String email, String password, String stayCode) {
    return """
    {"email": "%s", "password": "%s", "stayCode": "%s", "locale": "pt"}
    """
        .formatted(email, password, stayCode);
  }

  private ResultActions signUp(String json) throws Exception {
    return mvc.perform(post(GUESTS).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private int guestRows() {
    return jdbc.queryForObject("select count(*) from guest", Integer.class);
  }

  @Requirement("RIA-51")
  @Test
  void shouldCreateAccountWithoutTokenWhenStayIsActive() throws Exception {
    var stayId = givenActiveStay();

    var result =
        signUp(body(EMAIL, PASSWORD, ACTIVE_CODE))
            .andExpect(status().isCreated())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.LOCATION, matchesPattern(".*/api/v1/guests/[0-9a-f-]{36}$")))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.locale").value("pt"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn();

    var id = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id");
    var stored =
        jdbc.queryForMap(
            "select stay_id::text as stay_id, keycloak_id from guest where id = ?::uuid", id);
    assertThat(stored.get("stay_id")).isEqualTo(stayId);
    var keycloakUsers = TestKeycloak.usersWithEmail(EMAIL);
    assertThat(keycloakUsers)
        .singleElement()
        .satisfies(user -> {
          assertThat(user.getId()).isEqualTo(stored.get("keycloak_id"));
          assertThat(user.getFirstName()).isEqualTo("Rita");
          assertThat(user.getLastName()).isEqualTo("Almeida");
        });
    assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isTrue();
  }

  @Requirement("RIA-51")
  @Test
  void shouldRefuseEndedStayWithClearProblem() throws Exception {
    givenStay(ENDED_CODE, "2026-11-25", "2026-11-29");

    signUp(body(EMAIL, PASSWORD, ENDED_CODE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.title").value("Stay already ended"))
        .andExpect(jsonPath("$.detail").isNotEmpty());

    assertThat(guestRows()).isZero();
    assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
  }

  @Requirement("RIA-51")
  @Test
  void shouldAcceptSignUpOnCheckOutDay() throws Exception {
    givenStay(ACTIVE_CODE, "2026-11-27", "2026-11-30");

    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE)).andExpect(status().isCreated());
  }

  @Requirement("RIA-51")
  @Test
  void shouldRefuseUnknownStayCodeWithGenericProblem() throws Exception {
    signUp(body(EMAIL, PASSWORD, UNKNOWN_CODE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid stay code"));

    assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
  }

  // An unknown and an already used code get the same answer, so codes cannot be probed.
  @Requirement("RIA-51")
  @Test
  void shouldAnswerAlreadyUsedStayCodeExactlyLikeUnknownCode() throws Exception {
    givenActiveStay();
    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE)).andExpect(status().isCreated());

    var used =
        signUp(body("other.guest@example.test", PASSWORD, ACTIVE_CODE))
            .andExpect(status().isUnprocessableContent())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var unknown =
        signUp(body("other.guest@example.test", PASSWORD, UNKNOWN_CODE))
            .andExpect(status().isUnprocessableContent())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<String>read(used, "$.title"))
        .isEqualTo(JsonPath.<String>read(unknown, "$.title"));
    assertThat(JsonPath.<String>read(used, "$.detail"))
        .isEqualTo(JsonPath.<String>read(unknown, "$.detail"));
    assertThat(TestKeycloak.usersWithEmail("other.guest@example.test")).isEmpty();
  }

  @Requirement("RIA-51")
  @Test
  void shouldRefuseEmailThatAlreadyHasAnAccountWith409() throws Exception {
    givenActiveStay();
    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE)).andExpect(status().isCreated());
    givenStay(ENDED_CODE, "2026-11-29", "2026-12-03");

    signUp(body(EMAIL, PASSWORD, ENDED_CODE))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(guestRows()).isEqualTo(1);
  }

  @Requirement("RIA-51")
  @Test
  void shouldRefuseInvalidEmailWith400Problem() throws Exception {
    givenActiveStay();

    signUp(body("not-an-email", PASSWORD, ACTIVE_CODE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Requirement("RIA-51")
  @Test
  void shouldRefusePasswordShorterThan8CharactersWith400Problem() throws Exception {
    givenActiveStay();

    signUp(body(EMAIL, "Short-1", ACTIVE_CODE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
  }

  // ADR 0010 compensation: the Keycloak user is deleted when the local insert fails.
  @Requirement("RIA-51")
  @Test
  void shouldDeleteKeycloakUserWhenGuestInsertFails() throws Exception {
    givenActiveStay();
    doThrow(new DataIntegrityViolationException("simulated failure"))
        .when(guests)
        .saveAndFlush(any(Guest.class));

    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(not(containsString("simulated"))));

    assertThat(guestRows()).isZero();
    assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
  }

  // BR1: a stay the hotel has checked out early has ended, even before its planned check-out day.
  @Requirement("RIA-51")
  @Test
  void shouldRefuseStayCheckedOutBeforeItsCheckOutDate() throws Exception {
    givenStay(ACTIVE_CODE, "2026-11-28", "2026-12-02", "CHECKED_OUT");

    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.title").value("Stay already ended"));

    assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
  }

  // A double click sends the same sign-up twice at once: one account is created, and the guest
  // can log in with it, whichever request wins.
  @Requirement("RIA-51")
  @Test
  void shouldCreateOneUsableAccountWhenTheSameSignUpArrivesTwiceAtOnce() throws Exception {
    givenActiveStay();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var results = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 2; i++) {
        results.add(
            executor.submit(() -> {
              start.await();
              return signUp(body(EMAIL, PASSWORD, ACTIVE_CODE))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            }));
      }
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var result : results) {
        statuses.add(result.get());
      }

      assertThat(statuses).containsOnlyOnce(201);
    } finally {
      executor.shutdownNow();
    }
    assertThat(guestRows()).isEqualTo(1);
    var keycloakId = jdbc.queryForObject("select keycloak_id from guest", String.class);
    assertThat(TestKeycloak.usersWithEmail(EMAIL))
        .singleElement()
        .satisfies(user -> assertThat(user.getId()).isEqualTo(keycloakId));
    assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isTrue();
  }

  // The orphan age compares Keycloak's own creation time with the real time, not the business
  // clock: here that clock is fixed at 30 November 2026, days after the user was created, and a
  // user created moments ago must still count as a sign-up in progress (review of #28).
  @Requirement("RIA-51")
  @Test
  void shouldNotTakeOverAGuestUserCreatedMomentsAgoEvenWhenTheBusinessClockIsLater()
      throws Exception {
    givenActiveStay();
    var user = new UserRepresentation();
    user.setUsername(EMAIL);
    user.setEmail(EMAIL);
    user.setFirstName("Rita");
    user.setLastName("Almeida");
    user.setEnabled(true);
    String id;
    try (var response = TestKeycloak.realm().users().create(user)) {
      id = CreatedResponseUtil.getCreatedId(response);
    }
    var guestRole = TestKeycloak.realm().roles().get("GUEST").toRepresentation();
    TestKeycloak.realm().users().get(id).roles().realmLevel().add(List.of(guestRole));

    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE)).andExpect(status().isConflict());

    assertThat(guestRows()).isZero();
    assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isFalse();
  }

  @Requirement("RIA-51")
  @Test
  void shouldNeverWriteThePasswordToTheLogs(CapturedOutput output) throws Exception {
    givenActiveStay();

    signUp(body(EMAIL, PASSWORD, ACTIVE_CODE)).andExpect(status().isCreated());
    signUp(body(EMAIL, PASSWORD, UNKNOWN_CODE)).andExpect(status().isUnprocessableContent());
    signUp(body("not-an-email", PASSWORD, ACTIVE_CODE)).andExpect(status().isBadRequest());

    assertThat(output.getAll()).doesNotContain(PASSWORD);
  }
}
