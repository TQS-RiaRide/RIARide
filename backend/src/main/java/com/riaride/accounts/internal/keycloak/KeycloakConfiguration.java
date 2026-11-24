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
  // The orphan-age check compares Keycloak's own creation timestamps, so it uses the real time,
  // not the business clock (which tests fix to another date).
  KeycloakIdentityProvider keycloakIdentityProvider(KeycloakAdminProperties properties) {
    return new KeycloakIdentityProvider(RestClient.builder(), properties, Clock.systemUTC());
  }
}
