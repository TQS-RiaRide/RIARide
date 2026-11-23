package com.riaride.accounts.internal.domain;

import java.util.random.RandomGenerator;

// Generates random stay codes. Production uses SecureRandom, so codes cannot be guessed;
// tests pass a seeded generator. Uniqueness is checked by the caller and enforced by the database.
public class StayCodeGenerator {

  private final RandomGenerator random;

  public StayCodeGenerator(RandomGenerator random) {
    this.random = random;
  }

  public StayCode next() {
    var code = new char[StayCode.LENGTH];
    for (int i = 0; i < code.length; i++) {
      code[i] = StayCode.ALPHABET.charAt(random.nextInt(StayCode.ALPHABET.length()));
    }
    return new StayCode(new String(code));
  }
}
