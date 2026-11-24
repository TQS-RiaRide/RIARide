package com.riaride.acceptance;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.riaride.FixedClockConfiguration;
import com.riaride.TestKeycloak;
import com.riaride.TestMosquitto;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The single Spring context shared by all Cucumber scenarios: the full
// application on a random port, backed by PostgreSQL in Testcontainers
// (never an in-memory database) and by Keycloak with the realm from
// infra/keycloak, so tokens are real and validated against its JWKS, and by Mosquitto for
// telemetry.
// The containers start once per JVM. Today is fixed (FixedClockConfiguration),
// so date rules do not depend on when the build runs.
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfiguration.class)
public class CucumberSpringConfiguration {

  static final String REALM = TestKeycloak.REALM;

  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  // Shared with the integration tests, so Keycloak starts once per build.
  static final KeycloakContainer KEYCLOAK = TestKeycloak.container();

  // The Stripe API, stubbed for the payment scenarios (ADR 0008): CI needs no network or key.
  static final WireMockServer STRIPE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    POSTGRES.start();
    STRIPE.start();
  }

  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Token validation and the Admin API client used by guest sign-up (RIA-51).
    TestKeycloak.registerProperties(registry);
    registry.add("riaride.payments.stripe.api-base", STRIPE::baseUrl);
    // Mosquitto, for the scenarios where the simulator sends telemetry (RIA-59).
    TestMosquitto.registerProperties(registry);
  }

  @LocalServerPort int port;

  @Before
  public void configureRestAssured() {
    RestAssured.port = port;
    RestAssured.basePath = "";
  }
}
