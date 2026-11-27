package com.riaride.accounts.internal.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

// The platform's roles, kept as realm roles in Keycloak (ADR 0009). Operator, technician, hotel
// manager and admin are not domain classes: they exist only as these roles (Product
// Specification 3).
public enum Role {
  GUEST,
  OPERATOR,
  TECHNICIAN,
  HOTEL_MANAGER,
  ADMIN;

  // Keycloak adds its own roles to every token (default-roles-riaride, offline_access,
  // uma_authorization). They are not platform roles, so they are left out, as is any name that
  // does not match exactly.
  public static Set<Role> fromRealmRoles(Collection<String> realmRoles) {
    var known = Arrays.stream(values()).map(Role::name).collect(Collectors.toUnmodifiableSet());
    var roles = EnumSet.noneOf(Role.class);
    realmRoles.stream().filter(known::contains).map(Role::valueOf).forEach(roles::add);
    return roles;
  }
}
