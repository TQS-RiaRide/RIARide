package com.riaride.accounts.internal.application;

// The user to create in the identity provider. toString hides the password.
public record NewGuestUser(String email, String password, String firstName, String lastName) {

  @Override
  public String toString() {
    return "NewGuestUser[email="
        + email
        + ", password=********, firstName="
        + firstName
        + ", lastName="
        + lastName
        + "]";
  }
}
