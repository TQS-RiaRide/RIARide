package com.riaride.accounts.internal.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The backend's service client for the Keycloak Admin API (ADR 0010).
// The secret comes from the environment, never from the repository.
@ConfigurationProperties("riaride.keycloak.admin")
record KeycloakAdminProperties(String baseUrl, String realm, String clientId, String clientSecret) {

  @Override
  public String toString() {
    return "KeycloakAdminProperties[baseUrl="
        + baseUrl
        + ", realm="
        + realm
        + ", clientId="
        + clientId
        + ", clientSecret=********]";
  }
}
