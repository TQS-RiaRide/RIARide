package com.riaride;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

// Unit test (Surefire): checks module boundaries without starting Spring.
// Fails on a dependency cycle or on access to another module's internal packages.
// There are no modules yet, so this passes trivially until the first one is added.
class ModularityTests {

  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(RiaRideApplication.class).verify();
  }
}
