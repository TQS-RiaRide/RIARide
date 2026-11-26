package com.riaride.passes.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Integration test (Failsafe): the migration that gives every pass the end of its stay (RIA-87)
// on PostgreSQL in Testcontainers, applied to passes stored before it. A pass activated before
// RIA-87 had no expiry: the migration gives it the one its first trip would give now, so an active
// pass always has an expiry, which the database then enforces.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassStayEndsAtMigrationIT {

  // The last migration before RIA-87's.
  private static final String BEFORE_RIA_87 = "10";
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";
  private static final Instant FIRST_TRIP = Instant.parse("2026-11-30T10:00:00Z");
  // Check-out on 2 December: the stay ends at midnight in Vila Ria (UTC+0 in winter).
  private static final Instant STAY_ENDS = Instant.parse("2026-12-03T00:00:00Z");

  private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
  private JdbcTemplate jdbc;

  @BeforeAll
  void passesStoredBeforeRia87() {
    postgres.start();
    var dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    flyway(dataSource, BEFORE_RIA_87).migrate();
    var rita = guest("MIGR2345", "rita.almeida@example.test");
    oldPass(rita, "ONE_DAY", "CLOSED", FIRST_TRIP);
    oldPass(rita, "THREE_DAY", "CLOSED", FIRST_TRIP);
    oldPass(rita, "STAY", "ACTIVE", FIRST_TRIP);
    // A guest holds one open pass at a time, so the pass never used is another guest's.
    var tomas = guest("MIGS2345", "tomas.ferreira@example.test");
    oldPass(tomas, "ONE_DAY", "INACTIVE", null);
    flyway(dataSource, "latest").migrate();
  }

  // A guest whose stay checks out on 2 December.
  private UUID guest(String stayCode, String email) {
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
        """
        insert into guest (id, keycloak_id, stay_id, email, locale)
        values (?, ?, ?, ?, 'pt')
        """,
        guestId,
        UUID.randomUUID().toString(),
        stayId,
        email);
    return guestId;
  }

  @AfterAll
  void stopDatabase() {
    postgres.stop();
  }

  private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  // A pass as RIA-54 stored it: activated by its first trip, without an expiry.
  private void oldPass(UUID guestId, String type, String status, Instant activatedAt) {
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status,
                          activated_at)
        values (?, ?, ?, ?, 4000, 5000, 'HELD', ?)
        """,
        UUID.randomUUID(),
        guestId,
        type,
        status,
        activatedAt == null ? null : Timestamp.from(activatedAt));
  }

  private Instant column(String type, String status, String column) {
    var value =
        jdbc.queryForObject(
            "select " + column + " from pass where pass_type = ? and status = ?",
            Timestamp.class,
            type,
            status);
    return value == null ? null : value.toInstant();
  }

  @Requirement("RIA-87")
  @Test
  void shouldGiveEveryPassTheEndOfItsStay() {
    assertThat(
            jdbc.queryForList("select stay_ends_at from pass", Timestamp.class).stream()
                .map(Timestamp::toInstant))
        .hasSize(4)
        .containsOnly(STAY_ENDS);
  }

  // A pass activated before RIA-87 gets the expiry its first trip would give now.
  @Requirement("RIA-87")
  @Test
  void shouldGiveAnActivatedPassTheExpiryOfItsFirstTrip() {
    assertThat(column("ONE_DAY", "CLOSED", "expires_at"))
        .isEqualTo(Instant.parse("2026-12-01T10:00:00Z"));
    // 72 h after the first trip is after check-out, so the pass ends with the stay.
    assertThat(column("THREE_DAY", "CLOSED", "expires_at")).isEqualTo(STAY_ENDS);
    assertThat(column("STAY", "ACTIVE", "expires_at")).isEqualTo(STAY_ENDS);
  }

  @Requirement("RIA-87")
  @Test
  void shouldLeaveAPassThatWasNeverUsedWithoutAnExpiry() {
    assertThat(column("ONE_DAY", "INACTIVE", "expires_at")).isNull();
  }

  // From now on the database refuses an active pass without an expiry.
  @Requirement("RIA-87")
  @Test
  void shouldRefuseAnActivePassWithoutAnExpiry() {
    assertThatThrownBy(() ->
            jdbc.update(
                """
                update pass set expires_at = null where pass_type = 'STAY' and status = 'ACTIVE'
                """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
