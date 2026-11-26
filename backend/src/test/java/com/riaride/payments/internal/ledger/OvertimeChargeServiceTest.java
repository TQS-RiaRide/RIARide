package com.riaride.payments.internal.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.payments.Money;
import com.riaride.payments.OffSessionCharge;
import com.riaride.payments.OvertimeChargeResult;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.TransientPaymentException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): overtime is charged off-session to the card saved when the pass was bought
// (RIA-88, ADR 0008), once per idempotency key. A charge refused for good is recorded as FAILED and
// not tried again; its deposit fallback is RIA-114. A transient failure is thrown, with nothing
// recorded, so the caller retries with the same key.
@ExtendWith(MockitoExtension.class)
class OvertimeChargeServiceTest {

  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final String KEY = "trip-6f1c0e2a-8d3b-4e5f-9a1b-2c3d4e5f6a7b:overtime";
  private static final Money OVERTIME = Money.cents(400);

  @Mock private PaymentRepository payments;

  @Mock private PaymentGateway gateway;

  private OvertimeChargeService service;

  @BeforeEach
  void setUp() {
    service = new OvertimeChargeService(payments, gateway);
  }

  private void givenThePassWasBoughtWithASavedCard() {
    when(payments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(payments.findFirstByPassIdAndKind(PASS, Payment.Kind.PURCHASE))
        .thenReturn(
            Optional.of(
                Payment.purchase(
                    PASS, 7500, "pi_purchase", "purchase-1", "cus_rita", "pm_saved_card")));
  }

  private Payment saved() {
    var payment = ArgumentCaptor.forClass(Payment.class);
    verify(payments).save(payment.capture());
    return payment.getValue();
  }

  @Requirement("RIA-88")
  @Test
  void shouldChargeOffSessionToTheCardSavedWithThePurchase() {
    givenThePassWasBoughtWithASavedCard();
    when(gateway.chargeOffSession(new OffSessionCharge("cus_rita", "pm_saved_card", OVERTIME, KEY)))
        .thenReturn("pi_overtime");

    var result = service.charge(PASS, OVERTIME, KEY);

    assertThat(result).isEqualTo(OvertimeChargeResult.CHARGED);
    var payment = saved();
    assertThat(payment.getKind()).isEqualTo(Payment.Kind.OVERTIME);
    assertThat(payment.getStatus()).isEqualTo(Payment.Status.SUCCEEDED);
    assertThat(payment.getAmountCents()).isEqualTo(400);
    assertThat(payment.getProviderRef()).isEqualTo("pi_overtime");
    assertThat(payment.getPassId()).isEqualTo(PASS);
  }

  // A redelivered event, or a retry after the charge was recorded, does not charge again.
  @Requirement("RIA-88")
  @Test
  void shouldNotChargeAgainForAKeyAlreadyCharged() {
    when(payments.findByIdempotencyKey(KEY))
        .thenReturn(Optional.of(Payment.overtime(PASS, 400, "pi_overtime", KEY)));

    var result = service.charge(PASS, OVERTIME, KEY);

    assertThat(result).isEqualTo(OvertimeChargeResult.CHARGED);
    verifyNoInteractions(gateway);
    verify(payments, never()).save(any());
  }

  // Card declined or authentication required: retrying will not help.
  @Requirement("RIA-88")
  @Test
  void shouldRecordAChargeRefusedForGoodWithoutThrowing() {
    givenThePassWasBoughtWithASavedCard();
    when(gateway.chargeOffSession(any()))
        .thenThrow(new PermanentPaymentException("card_declined", "Your card was declined.", null));

    var result = service.charge(PASS, OVERTIME, KEY);

    assertThat(result).isEqualTo(OvertimeChargeResult.FAILED);
    var payment = saved();
    assertThat(payment.getKind()).isEqualTo(Payment.Kind.OVERTIME);
    assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
    assertThat(payment.getProviderRef()).isNull();
  }

  @Requirement("RIA-88")
  @Test
  void shouldNotTryAgainAChargeThatFailedForGood() {
    when(payments.findByIdempotencyKey(KEY))
        .thenReturn(Optional.of(Payment.failedOvertime(PASS, 400, KEY)));

    var result = service.charge(PASS, OVERTIME, KEY);

    assertThat(result).isEqualTo(OvertimeChargeResult.FAILED);
    verifyNoInteractions(gateway);
  }

  @Requirement("RIA-88")
  @Test
  void shouldThrowAFailureForNowAndRecordNothing() {
    givenThePassWasBoughtWithASavedCard();
    var unreachable = new TransientPaymentException("api_error", "Stripe request failed", null);
    when(gateway.chargeOffSession(any())).thenThrow(unreachable);

    assertThatThrownBy(() -> service.charge(PASS, OVERTIME, KEY)).isSameAs(unreachable);
    verify(payments, never()).save(any());
  }

  @Requirement("RIA-88")
  @Test
  void shouldRefuseAPassThatWasNeverPaid() {
    when(payments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(payments.findFirstByPassIdAndKind(PASS, Payment.Kind.PURCHASE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.charge(PASS, OVERTIME, KEY))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(gateway);
  }
}
