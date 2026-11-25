package com.riaride.passes.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.RowLocks;
import com.riaride.TestRows;
import com.riaride.TestcontainersConfiguration;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassPrice;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.domain.PassType;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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

// Integration test (Failsafe): the pass and pass_price tables (Flyway V4), their constraints and
// the pass row lock on PostgreSQL in Testcontainers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PassRepositoryIT {

  private static final String STAY_CODE = "PASS2345";
  private static final String LOCK_PASS = "select id from pass where id = ? for update nowait";

  @Autowired private PassRepository passes;

  @Autowired private PassPriceRepository prices;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private DataSource dataSource;

  @Autowired private PlatformTransactionManager transactionManager;

  private UUID guestId;

  @BeforeEach
  void guest() {
    guestId = TestRows.guest(jdbc, STAY_CODE, "rita.almeida@example.test");
  }

  // BR3: one base price per pass type for the whole group, seeded by V4.
  @Requirement("RIA-52")
  @Test
  void shouldHoldTheSeededBasePriceOfEveryPassType() {
    assertThat(prices.findAll())
        .extracting(PassPrice::getPassType, PassPrice::getPriceCents, PassPrice::getDepositCents)
        .containsExactlyInAnyOrder(
            tuple(PassType.ONE_DAY, 1000, 5000),
            tuple(PassType.THREE_DAY, 2500, 5000),
            tuple(PassType.STAY, 4000, 5000));
  }

  @Requirement("RIA-52")
  @Test
  void shouldFindTheOpenPassOfAGuestAndIgnoreTheClosedOnes() {
    TestRows.pass(jdbc, guestId, "CLOSED");
    var open = TestRows.pass(jdbc, guestId, "INACTIVE");

    assertThat(passes.findFirstByGuestIdAndStatusIn(guestId, PassStatus.OPEN))
        .get()
        .extracting(Pass::getId)
        .isEqualTo(open);
    assertThat(passes.existsByGuestIdAndStatusIn(guestId, PassStatus.OPEN)).isTrue();
  }

  @Requirement("RIA-52")
  @Test
  void shouldFindNoOpenPassWhenTheGuestHasOnlyClosedOnes() {
    TestRows.pass(jdbc, guestId, "CLOSED");

    assertThat(passes.findFirstByGuestIdAndStatusIn(guestId, PassStatus.OPEN)).isEmpty();
    assertThat(passes.existsByGuestIdAndStatusIn(guestId, PassStatus.OPEN)).isFalse();
  }

  // A pending pass whose payment was refused is removed, but never one that was paid meanwhile.
  @Requirement("RIA-52")
  @Test
  void shouldDeleteAPassOnlyWhileItHasTheGivenStatus() {
    var pending = TestRows.pass(jdbc, guestId, "PENDING");

    assertThat(passes.deleteByIdAndStatus(pending, PassStatus.INACTIVE)).isZero();
    assertThat(passes.existsById(pending)).isTrue();

    assertThat(passes.deleteByIdAndStatus(pending, PassStatus.PENDING)).isOne();
    assertThat(passes.existsById(pending)).isFalse();
  }

  // One open pass per guest (RIA-52): the partial unique index refuses a second one, pending
  // included, so a concurrent purchase fails before its card is charged.
  @Requirement("RIA-52")
  @Test
  void shouldRejectASecondOpenPassForTheSameGuest() {
    TestRows.pass(jdbc, guestId, "PENDING");

    assertThatThrownBy(() -> TestRows.pass(jdbc, guestId, "INACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldAllowANewPassOnceThePreviousOneIsClosed() {
    TestRows.pass(jdbc, guestId, "CLOSED");
    var price = prices.findById(PassType.ONE_DAY).orElseThrow();

    var saved = passes.saveAndFlush(Pass.purchase(guestId, price, "purchase-key-1"));

    assertThat(passes.findById(saved.getId()))
        .get()
        .satisfies(pass -> {
          assertThat(pass.getStatus()).isEqualTo(PassStatus.PENDING);
          assertThat(pass.getPassType()).isEqualTo(PassType.ONE_DAY);
          assertThat(pass.amountToCharge().cents()).isEqualTo(6000);
        });
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectAPassStatusTheDomainDoesNotKnowInTheDatabase() {
    var pass = TestRows.pass(jdbc, guestId, "INACTIVE");

    assertThatThrownBy(() -> jdbc.update("update pass set status = 'EXPIRED' where id = ?", pass))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // A trip start checks the pass inside its own transaction (PassUsageService.canStartTrip), so
  // the pass cannot change between that check and the trip being saved.
  @Requirement("RIA-54")
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void shouldLockThePassUntilTheTransactionEnds() {
    // Not rolled back: the guest from @BeforeEach is committed here and removed below.
    var passId = TestRows.pass(jdbc, guestId, "INACTIVE");
    try {
      var lockedInside =
          new TransactionTemplate(transactionManager)
              .execute(status -> {
                assertThat(passes.findByIdForUpdate(passId)).isPresent();
                return RowLocks.isLocked(dataSource, LOCK_PASS, passId);
              });

      assertThat(lockedInside).isTrue();
      assertThat(RowLocks.isLocked(dataSource, LOCK_PASS, passId)).isFalse();
    } finally {
      jdbc.update("delete from pass where guest_id = ?", guestId);
      jdbc.update("delete from guest where id = ?", guestId);
      jdbc.update("delete from stay where stay_code = ?", STAY_CODE);
    }
  }
}
