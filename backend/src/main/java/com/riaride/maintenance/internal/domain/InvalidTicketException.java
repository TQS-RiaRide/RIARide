package com.riaride.maintenance.internal.domain;

// A ticket detail breaks a rule. The field is the request field the value came from; the message
// explains the rule and never repeats the rejected value (QA Manual 3.4).
public class InvalidTicketException extends RuntimeException {

  private final String field;

  public InvalidTicketException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
