package com.riaride.fleet;

// No hub has the id another module gave (RIA-55).
public class UnknownHubException extends RuntimeException {

  public UnknownHubException() {
    super("No hub has this id.");
  }
}
