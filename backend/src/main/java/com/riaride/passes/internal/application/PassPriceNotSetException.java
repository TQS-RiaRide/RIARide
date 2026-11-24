package com.riaride.passes.internal.application;

public class PassPriceNotSetException extends RuntimeException {

  public PassPriceNotSetException() {
    super("The price of this pass type is not set yet.");
  }
}
