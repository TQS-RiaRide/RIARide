package com.riaride.passes.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassPrice;
import com.riaride.passes.internal.domain.PassType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the pass table on PostgreSQL in Testcontainers keeps the end of the
// guest's stay from the purchase, and the activation and expiry times of the first trip (RIA-87).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PassRepositoryIT {

  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final Instant FIRST_TRIP = Instant.parse("2026-11-30T10:00:10Z");
  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");

  @Autowired private PassRepository passes;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private TestEntityManager entityManager;

  private UUID guestId;

  @BeforeEach
  void guestWithAnActiveStay() {
    var stayId = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Rita Almeida', 'PASS2345', date '2026-11-28', date '2026-12-02', 'ACTIVE')
        """,
        stayId,
        MARES_CENTRO);
    guestId = UUID.randomUUID();
    jdbc.update(
        """
        insert into guest (id, keycloak_id, stay_id, email, locale)
        values (?, ?, ?, 'rita.almeida@example.test', 'pt')
        """,
        guestId,
        UUID.randomUUID().toString(),
        stayId);
  }

  private Instant column(UUID passId, String name) {
    var value =
        jdbc.queryForObject("select " + name + " from pass where id = ?", Timestamp.class, passId);
    return value == null ? null : value.toInstant();
  }

  @Requirement("RIA-87")
  @Test
  void shouldStoreTheEndOfTheStayAndTheExpiryOfTheFirstTrip() {
    var pass =
        Pass.purchase(
            guestId, new PassPrice(PassType.ONE_DAY, 1000, 5000), guestId + ":p1", STAY_ENDS);
    pass.confirmPayment();
    passes.saveAndFlush(pass);
    assertThat(column(pass.getId(), "stay_ends_at")).isEqualTo(STAY_ENDS);
    assertThat(column(pass.getId(), "expires_at")).isNull();

    var stored = passes.findById(pass.getId()).orElseThrow();
    stored.activate(FIRST_TRIP);
    passes.saveAndFlush(stored);
    entityManager.clear();

    assertThat(column(pass.getId(), "activated_at")).isEqualTo(FIRST_TRIP);
    assertThat(column(pass.getId(), "expires_at")).isEqualTo(FIRST_TRIP.plus(Duration.ofHours(24)));
    var reloaded = passes.findById(pass.getId()).orElseThrow();
    assertThat(reloaded.getStayEndsAt()).isEqualTo(STAY_ENDS);
    assertThat(reloaded.getExpiresAt()).isEqualTo(FIRST_TRIP.plus(Duration.ofHours(24)));
  }
}
