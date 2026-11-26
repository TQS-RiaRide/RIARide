package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Unit test (Surefire): the vehicle lifecycle as a table of every pair of states (RIA-91), matching
// the state diagram of the Product Specification, section 3.3. A status never "changes" to itself.
class VehicleLifecycleTest {

  @Requirement("RIA-91")
  @ParameterizedTest(name = "{0} -> {1}: {2}")
  @CsvSource({
    "AVAILABLE,   AVAILABLE,   false",
    "AVAILABLE,   RESERVED,    true",
    "AVAILABLE,   IN_USE,      false",
    "AVAILABLE,   MAINTENANCE, true",
    "AVAILABLE,   RETIRED,     true",
    "RESERVED,    AVAILABLE,   true",
    "RESERVED,    RESERVED,    false",
    "RESERVED,    IN_USE,      true",
    "RESERVED,    MAINTENANCE, true",
    "RESERVED,    RETIRED,     false",
    "IN_USE,      AVAILABLE,   true",
    "IN_USE,      RESERVED,    false",
    "IN_USE,      IN_USE,      false",
    "IN_USE,      MAINTENANCE, true",
    "IN_USE,      RETIRED,     false",
    "MAINTENANCE, AVAILABLE,   true",
    "MAINTENANCE, RESERVED,    false",
    "MAINTENANCE, IN_USE,      false",
    "MAINTENANCE, MAINTENANCE, false",
    "MAINTENANCE, RETIRED,     true",
    "RETIRED,     AVAILABLE,   false",
    "RETIRED,     RESERVED,    false",
    "RETIRED,     IN_USE,      false",
    "RETIRED,     MAINTENANCE, false",
    "RETIRED,     RETIRED,     false"
  })
  void shouldAllowOnlyTheTransitionsOfTheStateDiagram(
      VehicleStatus from, VehicleStatus to, boolean allowed) {
    assertThat(from.canChangeTo(to)).isEqualTo(allowed);
  }
}
