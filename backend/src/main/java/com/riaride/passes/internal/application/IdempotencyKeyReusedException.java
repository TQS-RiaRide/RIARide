package com.riaride.passes.internal.application;

public class IdempotencyKeyReusedException extends RuntimeException {

  public IdempotencyKeyReusedException() {
    super("This idempotency key was already used for another purchase.");
  }
}
