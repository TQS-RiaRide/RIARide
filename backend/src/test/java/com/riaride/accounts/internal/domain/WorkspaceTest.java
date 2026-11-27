package com.riaride.accounts.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): each role sees only the parts of the web app it needs (RIA-86). The
// operator manages the fleet and the tickets; the technician only works on tickets; the hotel
// manager runs the backoffice; the admin manages accounts and can act for operators and hotel
// managers (Product Specification 2.2 and the API roles); a guest has the guest dashboard.
class WorkspaceTest {

  @Requirement("RIA-86")
  @Test
  void shouldShowOperatorTheOperatorDashboardAndTicketsButNotTheHotelBackoffice() {
    var workspaces = Workspace.availableTo(Set.of(Role.OPERATOR));

    assertThat(workspaces)
        .containsExactly(Workspace.OPERATOR_DASHBOARD, Workspace.MAINTENANCE_TICKETS)
        .doesNotContain(Workspace.HOTEL_BACKOFFICE);
  }

  @Requirement("RIA-86")
  @Test
  void shouldShowGuestOnlyTheGuestDashboard() {
    assertThat(Workspace.availableTo(Set.of(Role.GUEST)))
        .containsExactly(Workspace.GUEST_DASHBOARD);
  }

  @Requirement("RIA-86")
  @Test
  void shouldShowTechnicianOnlyTheMaintenanceTickets() {
    assertThat(Workspace.availableTo(Set.of(Role.TECHNICIAN)))
        .containsExactly(Workspace.MAINTENANCE_TICKETS);
  }

  @Requirement("RIA-86")
  @Test
  void shouldShowHotelManagerOnlyTheHotelBackoffice() {
    assertThat(Workspace.availableTo(Set.of(Role.HOTEL_MANAGER)))
        .containsExactly(Workspace.HOTEL_BACKOFFICE);
  }

  @Requirement("RIA-86")
  @Test
  void shouldShowAdminEveryStaffWorkspaceAndAccountAdministrationButNotTheGuestDashboard() {
    assertThat(Workspace.availableTo(Set.of(Role.ADMIN)))
        .containsExactly(
            Workspace.OPERATOR_DASHBOARD,
            Workspace.MAINTENANCE_TICKETS,
            Workspace.HOTEL_BACKOFFICE,
            Workspace.ACCOUNT_ADMINISTRATION);
  }

  @Requirement("RIA-86")
  @Test
  void shouldShowNothingToAUserWithNoPlatformRole() {
    assertThat(Workspace.availableTo(EnumSet.noneOf(Role.class))).isEmpty();
  }

  // A user with several roles sees each workspace once, always in the same menu order.
  @Requirement("RIA-86")
  @Test
  void shouldJoinTheWorkspacesOfEveryRoleOnceInMenuOrder() {
    var workspaces =
        Workspace.availableTo(Set.of(Role.HOTEL_MANAGER, Role.TECHNICIAN, Role.OPERATOR));

    assertThat(workspaces)
        .containsExactly(
            Workspace.OPERATOR_DASHBOARD,
            Workspace.MAINTENANCE_TICKETS,
            Workspace.HOTEL_BACKOFFICE);
  }
}
