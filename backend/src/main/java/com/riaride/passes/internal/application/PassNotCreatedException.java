package com.riaride.passes.internal.application;

// The card was charged but the pass could not be stored, so the charge was refunded (or, if the
// refund failed too, it is logged for a manual refund).
public class PassNotCreatedException extends RuntimeException {

  public PassNotCreatedException(Throwable cause) {
    super("The pass could not be stored after the payment; the payment was refunded", cause);
  }
}
