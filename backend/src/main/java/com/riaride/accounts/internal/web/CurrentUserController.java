package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.domain.Role;
import com.riaride.accounts.internal.domain.Workspace;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The logged-in user (RIA-86): any valid token may read its own user. The roles are read from the
// token's realm roles (claim realm_access.roles), the same claim the security module authorizes
// with.
@RestController
@RequestMapping("/api/v1/me")
class CurrentUserController {

  @GetMapping
  CurrentUserResponse currentUser(JwtAuthenticationToken authentication) {
    var token = authentication.getToken();
    var roles = Role.fromRealmRoles(realmRoles(token));
    return new CurrentUserResponse(
        token.getSubject(),
        token.getClaimAsString("name"),
        token.getClaimAsString("email"),
        List.copyOf(roles),
        Workspace.availableTo(roles));
  }

  private static List<String> realmRoles(Jwt token) {
    if (!(token.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)
        || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
      return List.of();
    }
    return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }
}
