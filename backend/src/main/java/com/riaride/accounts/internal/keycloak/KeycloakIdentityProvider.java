package com.riaride.accounts.internal.keycloak;

import com.riaride.accounts.internal.application.EmailAlreadyRegisteredException;
import com.riaride.accounts.internal.application.GuestUser;
import com.riaride.accounts.internal.application.IdentityProvider;
import com.riaride.accounts.internal.application.IdentityProviderUnavailableException;
import com.riaride.accounts.internal.application.IdentityRejectedException;
import com.riaride.accounts.internal.application.NewGuestUser;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Adapter for the Keycloak Admin API (ADR 0010), authenticated with the backend's service client
// (client credentials). It never logs request bodies, so passwords do not reach the logs.
class KeycloakIdentityProvider implements IdentityProvider {

  static final String GUEST_ROLE = "GUEST";

  // A sign-up takes seconds. A guest-only user younger than this may belong to a sign-up still
  // in progress (a double click), so it is never taken over.
  static final Duration ORPHAN_MIN_AGE = Duration.ofMinutes(2);

  private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityProvider.class);

  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_OBJECTS =
      new ParameterizedTypeReference<>() {};

  private final RestClient rest;
  private final KeycloakAdminProperties properties;
  private final Clock clock;

  KeycloakIdentityProvider(
      RestClient.Builder builder, KeycloakAdminProperties properties, Clock clock) {
    this.rest = builder.baseUrl(properties.baseUrl()).build();
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public GuestUser createGuestUser(NewGuestUser user) {
    try {
      var token = accessToken();
      var existing = findUserByEmail(token, user.email());
      if (existing.isPresent()) {
        return new GuestUser(reuseOrphanGuest(token, existing.get(), user), false);
      }
      var id = createUser(token, user);
      try {
        assignGuestRole(token, id);
      } catch (RuntimeException failure) {
        deleteQuietly(token, id, failure);
        throw failure;
      }
      return new GuestUser(id, true);
    } catch (RestClientResponseException failure) {
      throw new IdentityProviderUnavailableException(
          "The Keycloak Admin API answered HTTP " + failure.getStatusCode().value(), failure);
    } catch (RestClientException failure) {
      throw new IdentityProviderUnavailableException(
          "The Keycloak Admin API could not be reached", failure);
    }
  }

  @Override
  public void deleteUser(String userId) {
    try {
      delete(accessToken(), userId);
    } catch (RestClientException failure) {
      throw new IdentityProviderUnavailableException(
          "The Keycloak Admin API request failed", failure);
    }
  }

  // An orphan is a user left behind by a sign-up that failed locally: it has the GUEST role and
  // nothing beyond the realm defaults, and it is old enough that no sign-up is still creating it.
  // Any other user with this email (an operator, a user created by hand, a sign-up in progress)
  // is never taken over, and its password is left as it is.
  private String reuseOrphanGuest(String token, Map<String, Object> existing, NewGuestUser user) {
    var userId = (String) existing.get("id");
    var roles = realmRoles(token, userId);
    var onlyGuest =
        roles.contains(GUEST_ROLE)
            && roles.stream()
                .allMatch(role ->
                    GUEST_ROLE.equals(role)
                        || role.startsWith("default-roles-")
                        || "offline_access".equals(role)
                        || "uma_authorization".equals(role));
    if (!onlyGuest || !isOldEnough(existing)) {
      throw new EmailAlreadyRegisteredException();
    }
    rest.put()
        .uri(usersPath() + "/{id}/reset-password", userId)
        .headers(headers -> headers.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(password(user.password()))
        .retrieve()
        .toBodilessEntity();
    return userId;
  }

  private boolean isOldEnough(Map<String, Object> user) {
    if (!(user.get("createdTimestamp") instanceof Number createdMillis)) {
      return false;
    }
    var created = Instant.ofEpochMilli(createdMillis.longValue());
    return !created.plus(ORPHAN_MIN_AGE).isAfter(clock.instant());
  }

  private String accessToken() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", properties.clientId());
    form.add("client_secret", properties.clientSecret());
    var response =
        rest.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    if (response == null || !(response.get("access_token") instanceof String token)) {
      throw new IdentityProviderUnavailableException("Keycloak returned no access token", null);
    }
    return token;
  }

  private Optional<Map<String, Object>> findUserByEmail(String token, String email) {
    var users =
        rest.get()
            .uri(usersPath() + "?email={email}&exact=true", email)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(LIST_OF_OBJECTS);
    return users == null ? Optional.empty() : users.stream().findFirst();
  }

  private String createUser(String token, NewGuestUser user) {
    var representation =
        Map.of(
            "username", user.email(),
            "email", user.email(),
            "firstName", user.firstName(),
            "lastName", user.lastName(),
            "enabled", true,
            "emailVerified", false,
            "credentials", List.of(password(user.password())));
    try {
      var response =
          rest.post()
              .uri(usersPath())
              .headers(headers -> headers.setBearerAuth(token))
              .contentType(MediaType.APPLICATION_JSON)
              .body(representation)
              .retrieve()
              .toBodilessEntity();
      return idFrom(response.getHeaders().getLocation());
    } catch (HttpClientErrorException.Conflict concurrentSignUp) {
      throw new EmailAlreadyRegisteredException();
    } catch (HttpClientErrorException.BadRequest rejected) {
      // Only here does a 400 mean Keycloak refused the user's details (for example a password
      // policy). A 400 on any other call is a misconfiguration and stays a 503.
      throw new IdentityRejectedException("Keycloak rejected the user (HTTP 400)", rejected);
    }
  }

  private static String idFrom(URI location) {
    if (location == null) {
      throw new IdentityProviderUnavailableException(
          "Keycloak did not return the new user's location", null);
    }
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private List<String> realmRoles(String token, String userId) {
    var roles =
        rest.get()
            .uri(usersPath() + "/{id}/role-mappings/realm", userId)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(LIST_OF_OBJECTS);
    return roles == null
        ? List.of()
        : roles.stream().map(role -> (String) role.get("name")).toList();
  }

  // Assigns GUEST unless the user already has it.
  private void assignGuestRole(String token, String userId) {
    var available =
        rest.get()
            .uri(usersPath() + "/{id}/role-mappings/realm/available", userId)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(LIST_OF_OBJECTS);
    var guestRole =
        available == null
            ? Optional.<Map<String, Object>>empty()
            : available.stream().filter(role -> GUEST_ROLE.equals(role.get("name"))).findFirst();
    if (guestRole.isEmpty()) {
      return;
    }
    rest.post()
        .uri(usersPath() + "/{id}/role-mappings/realm", userId)
        .headers(headers -> headers.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(List.of(guestRole.get()))
        .retrieve()
        .toBodilessEntity();
  }

  // Compensation inside the adapter: a failed delete is attached to the original error.
  private void deleteQuietly(String token, String userId, RuntimeException failure) {
    try {
      delete(token, userId);
    } catch (RuntimeException cleanup) {
      failure.addSuppressed(cleanup);
      log.warn(
          "Could not delete Keycloak user {} after a failed role assignment ({})",
          userId,
          cleanup.getClass().getSimpleName());
    }
  }

  private void delete(String token, String userId) {
    rest.delete()
        .uri(usersPath() + "/{id}", userId)
        .headers(headers -> headers.setBearerAuth(token))
        .retrieve()
        .onStatus(
            status -> status.isSameCodeAs(HttpStatus.NOT_FOUND),
            (request, response) -> {
              // Already gone: nothing to delete.
            })
        .toBodilessEntity();
  }

  private static Map<String, Object> password(String value) {
    return Map.of("type", "password", "value", value, "temporary", false);
  }

  private String usersPath() {
    return "/admin/realms/" + properties.realm() + "/users";
  }
}
