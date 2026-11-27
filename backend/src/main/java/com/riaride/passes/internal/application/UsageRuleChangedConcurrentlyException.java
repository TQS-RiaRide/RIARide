package com.riaride.passes.internal.application;

public class UsageRuleChangedConcurrentlyException extends RuntimeException {

  public UsageRuleChangedConcurrentlyException() {
    super("The rules of use were changed at the same moment by another request. Try again.");
  }
}
