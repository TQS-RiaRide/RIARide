package com.riaride;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Integration test (Failsafe): starts the full Spring context.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RiaRideApplicationIT {

  @Test
  void contextLoads() {}
}
