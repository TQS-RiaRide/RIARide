package com.riaride.fleet.internal.domain;

// A hub detail breaks a rule. The field is the request field the value came from; the message
// explains the rule and never repeats the rejected value (QA Manual 3.4).
public class InvalidHubException extends RuntimeException {

  private final String field;

  public InvalidHubException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
