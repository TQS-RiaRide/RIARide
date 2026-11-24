package com.riaride.passes.internal.application;

public class PassAlreadyOpenException extends RuntimeException {

  public PassAlreadyOpenException() {
    super("You already have a pass that is not used up. Use it before buying another.");
  }
}
