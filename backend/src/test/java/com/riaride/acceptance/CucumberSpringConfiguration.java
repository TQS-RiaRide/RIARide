package com.riaride.acceptance;

import com.riaride.FixedClockConfiguration;
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
// infra/keycloak, so tokens are real and validated against its JWKS.
// The containers start once per JVM. Today is fixed (FixedClockConfiguration),
// so date rules do not depend on when the build runs.
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfiguration.class)
public class CucumberSpringConfiguration {

	static final String REALM = "riaride";

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.0")
		.withRealmImportFile("/keycloak/riaride-realm.json");

	static {
		POSTGRES.start();
		KEYCLOAK.start();
	}

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
			() -> KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM);
	}

	@LocalServerPort
	int port;

	@Before
	public void configureRestAssured() {
		RestAssured.port = port;
		RestAssured.basePath = "";
	}

}
