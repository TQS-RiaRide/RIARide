package com.riaride.accounts.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestRows;
import com.riaride.TestcontainersConfiguration;
import com.riaride.accounts.internal.domain.Guest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the guest table (Flyway V2) and its constraints on PostgreSQL in
// Testcontainers. The constraints stop two concurrent sign-ups from both getting an account.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GuestRepositoryIT {

  private static final String EMAIL = "rita.almeida@example.test";

  @Autowired private GuestRepository guests;

  @Autowired private JdbcTemplate jdbc;

  private UUID stayId;

  @BeforeEach
  void stay() {
    stayId = TestRows.stay(jdbc, "GUES2345");
  }

  private static String keycloakId() {
    return UUID.randomUUID().toString();
  }

  @Requirement("RIA-51")
  @Test
  void shouldFindAGuestByTheirKeycloakId() {
    var keycloakId = keycloakId();
    guests.saveAndFlush(Guest.signUp(keycloakId, stayId, EMAIL, "pt"));

    assertThat(guests.findByKeycloakId(keycloakId))
        .get()
        .satisfies(guest -> {
          assertThat(guest.getStayId()).isEqualTo(stayId);
          assertThat(guest.getEmail()).isEqualTo(EMAIL);
          assertThat(guest.getLocale()).isEqualTo("pt");
        });
    assertThat(guests.findByKeycloakId(keycloakId())).isEmpty();
  }

  @Requirement("RIA-51")
  @Test
  void shouldTellWhetherAStayAnEmailOrAKeycloakIdIsTaken() {
    var keycloakId = keycloakId();
    guests.saveAndFlush(Guest.signUp(keycloakId, stayId, EMAIL, "en"));

    assertThat(guests.existsByStayId(stayId)).isTrue();
    assertThat(guests.existsByEmail(EMAIL)).isTrue();
    assertThat(guests.existsByKeycloakId(keycloakId)).isTrue();
    assertThat(guests.existsByStayId(UUID.randomUUID())).isFalse();
    assertThat(guests.existsByEmail("other@example.test")).isFalse();
    assertThat(guests.existsByKeycloakId(keycloakId())).isFalse();
  }

  // One account per stay code (Product Owner decision, RIA-51).
  @Requirement("RIA-51")
  @Test
  void shouldRejectASecondAccountForTheSameStay() {
    guests.saveAndFlush(Guest.signUp(keycloakId(), stayId, EMAIL, "pt"));

    assertThatThrownBy(() ->
            guests.saveAndFlush(Guest.signUp(keycloakId(), stayId, "other@example.test", "pt")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Requirement("RIA-51")
  @Test
  void shouldRejectASecondAccountWithTheSameEmail() {
    guests.saveAndFlush(Guest.signUp(keycloakId(), stayId, EMAIL, "pt"));
    var otherStay = TestRows.stay(jdbc, "GUES6789");

    assertThatThrownBy(
            () -> guests.saveAndFlush(Guest.signUp(keycloakId(), otherStay, EMAIL, "pt")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Requirement("RIA-51")
  @Test
  void shouldRejectAGuestForAStayThatDoesNotExist() {
    var unknownStay = UUID.randomUUID();

    assertThatThrownBy(
            () -> guests.saveAndFlush(Guest.signUp(keycloakId(), unknownStay, EMAIL, "pt")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // The UI languages are Portuguese and English only.
  @Requirement("RIA-51")
  @Test
  void shouldRejectALocaleOtherThanPortugueseOrEnglishInTheDatabase() {
    var guest = guests.saveAndFlush(Guest.signUp(keycloakId(), stayId, EMAIL, "pt"));

    assertThatThrownBy(
            () -> jdbc.update("update guest set locale = 'fr' where id = ?", guest.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
