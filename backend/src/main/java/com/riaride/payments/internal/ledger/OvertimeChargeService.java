package com.riaride.payments.internal.ledger;

import com.riaride.payments.Money;
import com.riaride.payments.OffSessionCharge;
import com.riaride.payments.OvertimeChargeResult;
import com.riaride.payments.OvertimeCharges;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PermanentPaymentException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// Charges overtime off-session to the card saved with the pass's purchase (RIA-88, ADR 0008). The
// provider is called outside any database transaction, so a slow provider holds no connection:
// each read and write is its own short transaction, as in the purchase (PassBook). The idempotency
// key goes to the provider too, so a retry after the charge but before it is recorded gets the
// provider's cached result instead of a second charge, and the unique key in the ledger lets only
// one attempt record it.
@Service
class OvertimeChargeService implements OvertimeCharges {

  private static final Logger log = LoggerFactory.getLogger(OvertimeChargeService.class);

  private final PaymentRepository payments;
  private final PaymentGateway gateway;

  OvertimeChargeService(PaymentRepository payments, PaymentGateway gateway) {
    this.payments = payments;
    this.gateway = gateway;
  }

  @Override
  public OvertimeChargeResult charge(UUID passId, Money amount, String idempotencyKey) {
    var recorded = payments.findByIdempotencyKey(idempotencyKey);
    if (recorded.isPresent()) {
      return resultOf(recorded.get());
    }
    var purchase =
        payments
            .findFirstByPassIdAndKind(passId, Payment.Kind.PURCHASE)
            .orElseThrow(() -> new IllegalStateException("The pass has no purchase payment"));
    try {
      var paymentRef =
          gateway.chargeOffSession(
              new OffSessionCharge(
                  purchase.getCustomerRef(),
                  purchase.getPaymentMethodRef(),
                  amount,
                  idempotencyKey));
      record(Payment.overtime(passId, amount.cents(), paymentRef, idempotencyKey));
      return OvertimeChargeResult.CHARGED;
    } catch (PermanentPaymentException refused) {
      log.warn("Overtime charge for pass {} refused ({})", passId, refused.reason());
      record(Payment.failedOvertime(passId, amount.cents(), idempotencyKey));
      return OvertimeChargeResult.FAILED;
    }
  }

  @Override
  public void recordUnpaid(UUID passId, Money amount, String idempotencyKey) {
    if (payments.findByIdempotencyKey(idempotencyKey).isPresent()) {
      return;
    }
    log.error("Overtime charge for pass {} given up after its last attempt", passId);
    record(Payment.failedOvertime(passId, amount.cents(), idempotencyKey));
  }

  // Another attempt with the same key recorded it first: the provider gave both the same result.
  private void record(Payment payment) {
    try {
      payments.save(payment);
    } catch (DataIntegrityViolationException recordedConcurrently) {
      log.info("Overtime payment {} was already recorded", payment.getIdempotencyKey());
    }
  }

  private static OvertimeChargeResult resultOf(Payment payment) {
    return payment.getStatus() == Payment.Status.SUCCEEDED
        ? OvertimeChargeResult.CHARGED
        : OvertimeChargeResult.FAILED;
  }
}
