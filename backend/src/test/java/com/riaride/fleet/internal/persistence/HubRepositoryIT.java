package com.riaride.fleet.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.RowLocks;
import com.riaride.TestcontainersConfiguration;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Integration test (Failsafe): the hub table (Flyway V3), its constraints and its row lock on
// PostgreSQL in Testcontainers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class HubRepositoryIT {

  private static final String MARES_PRAIA = "00000000-0000-0000-0000-000000000002";
  private static final String LOCK_HUB = "select id from hub where id = ? for update nowait";

  @Autowired private HubRepository hubs;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private DataSource dataSource;

  @Autowired private PlatformTransactionManager transactionManager;

  private static Hub touristSpot(String name) {
    return Hub.register(name, HubKind.TOURIST_SPOT, null, 40.64, -8.65, 6);
  }

  @Requirement("RIA-56")
  @Test
  void shouldTellAnExistingNameOnlyWhenItMatchesExactly() {
    hubs.saveAndFlush(touristSpot("Miradouro da Ria"));

    assertThat(hubs.existsByName("Miradouro da Ria")).isTrue();
    assertThat(hubs.existsByName("miradouro da ria")).isFalse();
    assertThat(hubs.existsByName("Miradouro")).isFalse();
  }

  @Requirement("RIA-56")
  @Test
  void shouldRejectTwoHubsWithTheSameName() {
    hubs.saveAndFlush(touristSpot("Miradouro da Ria"));

    assertThatThrownBy(() -> hubs.saveAndFlush(touristSpot("Miradouro da Ria")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // BR10: the smallest capacity is one bike; the database refuses zero even if the domain is
  // bypassed.
  @Requirement("RIA-56")
  @Test
  void shouldRejectAHubWithNoRoomInTheDatabase() {
    var hub = hubs.saveAndFlush(touristSpot("Miradouro da Ria"));

    assertThatThrownBy(() -> jdbc.update("update hub set capacity = 0 where id = ?", hub.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Requirement("RIA-56")
  @Test
  void shouldRejectATouristSpotThatBelongsToAHotelInTheDatabase() {
    var hub = hubs.saveAndFlush(touristSpot("Miradouro da Ria"));

    assertThatThrownBy(() ->
            jdbc.update("update hub set hotel_id = ?::uuid where id = ?", MARES_PRAIA, hub.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // Two trips ending at the same hub check its free slots one after the other (BR10): the hub
  // stays locked until the transaction that read it for update ends.
  @Requirement("RIA-55")
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void shouldLockTheHubUntilTheTransactionEnds() {
    // Committed, so named uniquely: a failed cleanup must not block later runs.
    var hubId = hubs.saveAndFlush(touristSpot("Lock test " + UUID.randomUUID())).getId();
    try {
      var lockedInside =
          new TransactionTemplate(transactionManager)
              .execute(status -> {
                assertThat(hubs.findByIdForUpdate(hubId)).isPresent();
                return RowLocks.isLocked(dataSource, LOCK_HUB, hubId);
              });

      assertThat(lockedInside).isTrue();
      assertThat(RowLocks.isLocked(dataSource, LOCK_HUB, hubId)).isFalse();
    } finally {
      hubs.deleteById(hubId);
    }
  }
}
