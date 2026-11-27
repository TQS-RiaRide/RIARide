package com.riaride.accounts.internal.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// The parts of the web app a user can open, in menu order (RIA-86). A user sees the workspaces
// of all their roles; the API still checks the role on every request, so this only decides what
// the UI offers.
public enum Workspace {
  GUEST_DASHBOARD(Role.GUEST),
  OPERATOR_DASHBOARD(Role.OPERATOR, Role.ADMIN),
  MAINTENANCE_TICKETS(Role.OPERATOR, Role.TECHNICIAN, Role.ADMIN),
  HOTEL_BACKOFFICE(Role.HOTEL_MANAGER, Role.ADMIN),
  ACCOUNT_ADMINISTRATION(Role.ADMIN);

  private final Set<Role> allowedRoles;

  Workspace(Role... allowedRoles) {
    this.allowedRoles = EnumSet.copyOf(List.of(allowedRoles));
  }

  public static List<Workspace> availableTo(Set<Role> roles) {
    return Arrays.stream(values())
        .filter(workspace -> workspace.allowedRoles.stream().anyMatch(roles::contains))
        .toList();
  }
}
