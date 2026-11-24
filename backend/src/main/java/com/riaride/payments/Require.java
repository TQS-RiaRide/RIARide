package com.riaride.payments;

// Argument checks shared by the gateway's request records.
final class Require {

  private Require() {}

  static String text(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  static <T> T present(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
