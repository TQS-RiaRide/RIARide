package com.riaride.passes.internal.web;

class NoCurrentPassException extends RuntimeException {

  NoCurrentPassException() {
    super("The guest holds no open pass.");
  }
}
