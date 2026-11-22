package com.riaride;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// PostgreSQL in Testcontainers for integration tests (never an in-memory database).
// @ServiceConnection points the DataSource at the container. Spring caches the context,
// so tests that import this configuration share one container.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:17-alpine");
	}

}
