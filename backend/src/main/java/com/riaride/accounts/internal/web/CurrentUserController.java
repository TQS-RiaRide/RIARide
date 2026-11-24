package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.domain.Role;
import com.riaride.accounts.internal.domain.Workspace;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The logged-in user (RIA-86): any valid token may read its own user. Roles come from the
// authorities the security module derived from the token's realm roles.
@RestController
@RequestMapping("/api/v1/me")
class CurrentUserController {

  private static final String ROLE_PREFIX = "ROLE_";

  @GetMapping
  CurrentUserResponse currentUser(JwtAuthenticationToken authentication) {
    var token = authentication.getToken();
    var roles = Role.fromRealmRoles(realmRoles(authentication));
    return new CurrentUserResponse(
        token.getSubject(),
        token.getClaimAsString("name"),
        token.getClaimAsString("email"),
        List.copyOf(roles),
        Workspace.availableTo(roles));
  }

  private static List<String> realmRoles(JwtAuthenticationToken authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .toList();
  }
}
