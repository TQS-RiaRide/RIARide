package com.riaride.payments.internal.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestRows;
import com.riaride.TestcontainersConfiguration;
import com.riaride.payments.Money;
import com.riaride.payments.PurchaseResult;
import com.riaride.payments.RecordedPurchase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the payment ledger on the payment table (Flyway V4) in PostgreSQL in
// Testcontainers. The unique idempotency key is the last guard against charging twice
// (architecture, "No duplicate charge").
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaPaymentLedger.class})
class JpaPaymentLedgerIT {

  private static final String KEY = "guest-1:purchase-key-1";
  private static final PurchaseResult PAID =
      new PurchaseResult("pi_test_1", "cus_test_1", "pm_test_1");

  @Autowired private JpaPaymentLedger ledger;

  @Autowired private PaymentRepository payments;

  @Autowired private JdbcTemplate jdbc;

  private UUID passId;

  @BeforeEach
  void paidPass() {
    var guestId = TestRows.guest(jdbc, "LEDG2345", "rita.almeida@example.test");
    passId = TestRows.pass(jdbc, guestId, "INACTIVE");
  }

  @Requirement("RIA-52")
  @Test
  void shouldFindARecordedPurchaseByItsIdempotencyKey() {
    ledger.recordPurchase(passId, KEY, Money.cents(9000), PAID);
    payments.flush();

    assertThat(ledger.findPurchase(KEY)).contains(new RecordedPurchase(passId, "pi_test_1"));
    assertThat(ledger.findPurchase("guest-1:purchase-key-2")).isEmpty();
  }

  // Kept for the deposit refund and off-session overtime charges.
  @Requirement("RIA-52")
  @Test
  void shouldKeepTheProviderReferencesOfThePurchase() {
    ledger.recordPurchase(passId, KEY, Money.cents(9000), PAID);
    payments.flush();

    var row =
        jdbc.queryForMap(
            "select kind, status, amount_cents, customer_ref, payment_method_ref from payment"
                + " where idempotency_key = ?",
            KEY);
    assertThat(row)
        .containsEntry("kind", "PURCHASE")
        .containsEntry("status", "SUCCEEDED")
        .containsEntry("amount_cents", 9000L)
        .containsEntry("customer_ref", "cus_test_1")
        .containsEntry("payment_method_ref", "pm_test_1");
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectASecondPaymentWithTheSameIdempotencyKey() {
    ledger.recordPurchase(passId, KEY, Money.cents(9000), PAID);
    payments.flush();

    assertThatThrownBy(
            () -> {
              ledger.recordPurchase(passId, KEY, Money.cents(9000), PAID);
              payments.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Requirement("RIA-52")
  @Test
  void shouldRejectAPaymentForAPassThatDoesNotExist() {
    var unknownPass = UUID.randomUUID();

    assertThatThrownBy(
            () -> {
              ledger.recordPurchase(unknownPass, KEY, Money.cents(9000), PAID);
              payments.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
