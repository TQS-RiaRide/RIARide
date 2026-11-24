package com.riaride.accounts.internal.application;

// The identity provider refused the account's details, for example a password that breaks
// a realm password policy. Not an outage: retrying the same request will fail again.
public class IdentityRejectedException extends RuntimeException {

  public IdentityRejectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
