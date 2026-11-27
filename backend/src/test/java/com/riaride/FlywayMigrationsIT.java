package com.riaride;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the Flyway migrations on PostgreSQL in Testcontainers.
// Flyway owns the schema and Hibernate only validates it, so the context starting proves the
// entities match the tables; this test checks the migrations themselves and the reference data.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationsIT {

  @Autowired private Flyway flyway;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void shouldApplyEveryMigrationWithNonePendingAndMatchingChecksums() {
    var info = flyway.info();

    assertThat(info.pending()).isEmpty();
    assertThat(info.applied())
        .allSatisfy(migration -> assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS))
        .isNotEmpty();
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
  }

  // Reference data every environment needs: the group's hotels (fixed ids carried in the
  // hotel_id token claim) and the base prices (BR3).
  @Test
  void shouldSeedTheHotelsAndTheBasePrices() {
    assertThat(jdbc.queryForList("select name from hotel order by id", String.class))
        .containsExactly("Marés Centro", "Marés Praia", "Marés Salinas");
    assertThat(jdbc.queryForList("select pass_type from pass_price", String.class))
        .containsExactlyInAnyOrder("ONE_DAY", "THREE_DAY", "STAY");
  }
}
