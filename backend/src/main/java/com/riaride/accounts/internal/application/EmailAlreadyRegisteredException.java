package com.riaride.accounts.internal.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

  public EmailAlreadyRegisteredException() {
    super("An account with this email already exists.");
  }
}
