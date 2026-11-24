package com.riaride.accounts.internal.application;

// The identity provider could not be reached or answered with an error.
public class IdentityProviderUnavailableException extends RuntimeException {

  public IdentityProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
