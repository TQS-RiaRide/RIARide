package com.riaride.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

// Maps Keycloak realm roles (claim realm_access.roles) to Spring authorities: HOTEL_MANAGER -> ROLE_HOTEL_MANAGER.
class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	static final String ROLE_PREFIX = "ROLE_";

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		if (!(jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)
			|| !(realmAccess.get("roles") instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
			.filter(String.class::isInstance)
			.map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
			.toList();
	}

}
