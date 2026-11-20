package com.riaride.accounts.internal.keycloak;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakAdminProperties.class)
class KeycloakConfiguration {

	@Bean
	KeycloakIdentityProvider keycloakIdentityProvider(KeycloakAdminProperties properties, Clock clock) {
		return new KeycloakIdentityProvider(RestClient.builder(), properties, clock);
	}

}
