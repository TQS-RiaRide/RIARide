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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Charges overtime off-session to the card saved with the pass's purchase (RIA-88, ADR 0008). The
// idempotency key goes to the provider too, so a retry after the charge but before it is recorded
// gets the provider's cached result instead of a second charge.
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
  @Transactional
  public OvertimeChargeResult charge(UUID passId, Money amount, String idempotencyKey) {
    var recorded = payments.findByIdempotencyKey(idempotencyKey);
    if (recorded.isPresent()) {
      return recorded.get().getStatus() == Payment.Status.SUCCEEDED
          ? OvertimeChargeResult.CHARGED
          : OvertimeChargeResult.FAILED;
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
      payments.save(Payment.overtime(passId, amount.cents(), paymentRef, idempotencyKey));
      return OvertimeChargeResult.CHARGED;
    } catch (PermanentPaymentException refused) {
      log.warn("Overtime charge for pass {} refused ({})", passId, refused.reason());
      payments.save(Payment.failedOvertime(passId, amount.cents(), idempotencyKey));
      return OvertimeChargeResult.FAILED;
    }
  }
}
