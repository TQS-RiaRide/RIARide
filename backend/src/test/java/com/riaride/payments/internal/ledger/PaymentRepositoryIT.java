package com.riaride.payments.internal.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the payment table on PostgreSQL in Testcontainers takes overtime
// charges, succeeded or refused for good (Flyway V12), next to the purchase whose saved card they
// use (RIA-88).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PaymentRepositoryIT {

  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

  @Autowired private PaymentRepository payments;

  @Autowired private JdbcTemplate jdbc;

  private UUID passId;

  @BeforeEach
  void aPaidPass() {
    var stayId = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Rita Almeida', 'PAYS2345', date '2026-11-28', date '2026-12-02', 'ACTIVE')
        """,
        stayId,
        MARES_CENTRO);
    var guestId = UUID.randomUUID();
    jdbc.update(
        """
        insert into guest (id, keycloak_id, stay_id, email, locale)
        values (?, ?, ?, 'rita.almeida@example.test', 'pt')
        """,
        guestId,
        UUID.randomUUID().toString(),
        stayId);
    passId = UUID.randomUUID();
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status,
                          activated_at, expires_at, stay_ends_at)
        values (?, ?, 'STAY', 'ACTIVE', 4000, 5000, 'HELD', timestamptz '2026-11-30T10:00:00Z',
                timestamptz '2026-12-03T00:00:00Z', timestamptz '2026-12-03T00:00:00Z')
        """,
        passId,
        guestId);
    payments.saveAndFlush(
        Payment.purchase(passId, 9000, "pi_purchase", "purchase-1", "cus_rita", "pm_saved_card"));
  }

  @Requirement("RIA-88")
  @Test
  void shouldFindThePurchaseWhoseCardTheOvertimeUses() {
    var purchase = payments.findFirstByPassIdAndKind(passId, Payment.Kind.PURCHASE).orElseThrow();

    assertThat(purchase.getCustomerRef()).isEqualTo("cus_rita");
    assertThat(purchase.getPaymentMethodRef()).isEqualTo("pm_saved_card");
  }

  @Requirement("RIA-88")
  @Test
  void shouldStoreAnOvertimeCharge() {
    payments.saveAndFlush(Payment.overtime(passId, 400, "pi_overtime", "trip-1:overtime"));

    var stored =
        jdbc.queryForMap(
            "select kind, status, amount_cents, provider_ref from payment where idempotency_key = ?",
            "trip-1:overtime");
    assertThat(stored)
        .containsEntry("kind", "OVERTIME")
        .containsEntry("status", "SUCCEEDED")
        .containsEntry("amount_cents", 400L)
        .containsEntry("provider_ref", "pi_overtime");
  }

  // A refused charge has no payment at the provider to refer to.
  @Requirement("RIA-88")
  @Test
  void shouldStoreAnOvertimeChargeRefusedForGoodWithoutAProviderReference() {
    payments.saveAndFlush(Payment.failedOvertime(passId, 400, "trip-2:overtime"));

    var stored =
        jdbc.queryForMap(
            "select kind, status, provider_ref from payment where idempotency_key = ?",
            "trip-2:overtime");
    assertThat(stored)
        .containsEntry("kind", "OVERTIME")
        .containsEntry("status", "FAILED")
        .containsEntry("provider_ref", null);
  }
}
