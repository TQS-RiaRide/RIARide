package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.domain.Role;
import com.riaride.accounts.internal.domain.Workspace;
import java.util.List;

// The logged-in user as the web app needs it after login: who they are, their platform roles and
// the workspaces to show in the menu (RIA-86).
record CurrentUserResponse(
    String id, String name, String email, List<Role> roles, List<Workspace> workspaces) {}
