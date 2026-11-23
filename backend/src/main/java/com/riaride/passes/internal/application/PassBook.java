package com.riaride.passes.internal.application;

import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.persistence.PassRepository;
import com.riaride.payments.PaymentLedger;
import com.riaride.payments.PurchaseResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// The database side of a purchase, each step in its own transaction: reserve the pending pass
// before the charge, then confirm it with its payment, or release it if the payment failed.
@Component
public class PassBook {

  private final PassRepository passes;
  private final PaymentLedger ledger;

  public PassBook(PassRepository passes, PaymentLedger ledger) {
    this.passes = passes;
    this.ledger = ledger;
  }

  // The one-open-pass index (uq_pass_open_per_guest) refuses a concurrent purchase here,
  // before its card is charged.
  @Transactional
  public void reserve(Pass pass) {
    passes.saveAndFlush(pass);
  }

  // Stores the paid pass and its payment together, so there is never one without the other.
  @Transactional
  public void confirm(Pass pass, String idempotencyKey, PurchaseResult result) {
    var stored =
        passes
            .findById(pass.getId())
            .orElseThrow(() -> new IllegalStateException("No reserved pass"));
    stored.confirmPayment();
    pass.confirmPayment();
    passes.saveAndFlush(stored);
    ledger.recordPurchase(stored.getId(), idempotencyKey, stored.amountToCharge(), result);
  }

  // Deletes the reservation only while it is still PENDING: never a pass that a concurrent request
  // with the same key has already confirmed.
  @Transactional
  public void releasePending(Pass pass) {
    passes.deleteByIdAndStatus(pass.getId(), PassStatus.PENDING);
  }
}
