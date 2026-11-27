package com.riaride.accounts.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the realm roles of a token become the platform's roles. Keycloak also puts
// its own roles in the token (default-roles-riaride, offline_access, uma_authorization), and they
// grant nothing here.
class RoleTest {

  @Requirement("RIA-86")
  @Test
  void shouldReadEveryPlatformRoleByItsRealmName() {
    var roles =
        Role.fromRealmRoles(List.of("GUEST", "OPERATOR", "TECHNICIAN", "HOTEL_MANAGER", "ADMIN"));

    assertThat(roles)
        .containsExactly(
            Role.GUEST, Role.OPERATOR, Role.TECHNICIAN, Role.HOTEL_MANAGER, Role.ADMIN);
  }

  @Requirement("RIA-86")
  @Test
  void shouldIgnoreKeycloakDefaultRolesAndUnknownRoles() {
    var roles =
        Role.fromRealmRoles(
            List.of("default-roles-riaride", "offline_access", "uma_authorization", "OPERATOR"));

    assertThat(roles).containsExactly(Role.OPERATOR);
  }

  @Requirement("RIA-86")
  @Test
  void shouldNotMatchARoleWrittenInAnotherCase() {
    assertThat(Role.fromRealmRoles(List.of("operator", "Admin"))).isEmpty();
  }

  @Requirement("RIA-86")
  @Test
  void shouldReturnNoRoleWhenTheTokenHasNone() {
    assertThat(Role.fromRealmRoles(List.of())).isEmpty();
  }
}
