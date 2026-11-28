package com.riaride.passes.internal.domain;

// A rule of use breaks a limit. The field is the request field the value came from; the message
// explains the limit and never repeats the rejected value (QA Manual 3.4).
public class InvalidUsageRuleException extends RuntimeException {

  private final String field;

  public InvalidUsageRuleException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
