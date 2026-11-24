package com.riaride.passes.internal.application;

public class NoActiveStayException extends RuntimeException {

  public NoActiveStayException() {
    super("Only guests with an active stay can buy a pass.");
  }
}
