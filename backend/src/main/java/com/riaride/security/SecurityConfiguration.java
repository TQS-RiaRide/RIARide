package com.riaride.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

// Stateless bearer-token API. Every endpoint needs a valid Keycloak token except health and guest
// sign-up. Modules restrict their endpoints by role with @PreAuthorize, and every endpoint with a
// role also gets the same rule here, so a caller without the role gets 403 before the body, path
// or query is read and converted, and learns nothing about the input rules. RoleAccessMatrixIT
// keeps both in step: it calls every endpoint with every role, and fails on an endpoint it does
// not list.
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

  @Bean
  ProblemDetailsSecurityHandler problemDetailsSecurityHandler(JsonMapper json) {
    return new ProblemDetailsSecurityHandler(json);
  }

  // The body is a single chained return so the analyzer can see the chain is stateless and
  // bearer-token only, which is what makes disabling CSRF safe here.
  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailsSecurityHandler problems) {
    return http
        // No cookies or sessions, so no CSRF token: requests authenticate with a bearer token only.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers("/actuator/health", "/actuator/health/**")
                .permitAll()
                // Guest sign-up (RIA-51): the guest has no account, and so no token, yet.
                .requestMatchers(HttpMethod.POST, "/api/v1/guests")
                .permitAll()
                // Stay registration (RIA-50): hotel managers and admins only.
                .requestMatchers(HttpMethod.POST, "/api/v1/stays")
                .hasAnyRole("HOTEL_MANAGER", "ADMIN")
                // Vehicles at a hub (RIA-53, RIA-59): guests choose a bike, operators and admins
                // follow the fleet.
                .requestMatchers(HttpMethod.GET, "/api/v1/hubs/*/vehicles")
                .hasAnyRole("GUEST", "OPERATOR", "ADMIN")
                // Hub registration (RIA-56): operators and admins only.
                .requestMatchers(HttpMethod.POST, "/api/v1/hubs")
                .hasAnyRole("OPERATOR", "ADMIN")
                // Vehicle registration at a hub (RIA-57): operators and admins only.
                .requestMatchers(HttpMethod.POST, "/api/v1/hubs/*/vehicles")
                .hasAnyRole("OPERATOR", "ADMIN")
                // Activating, deactivating or retiring a bike (RIA-91): operators and admins only.
                .requestMatchers(HttpMethod.PUT, "/api/v1/vehicles/*/status")
                .hasAnyRole("OPERATOR", "ADMIN")
                // Buying a pass (RIA-52): guests only.
                .requestMatchers(HttpMethod.POST, "/api/v1/passes")
                .hasRole("GUEST")
                // Starting a trip and reading it (RIA-54): guests only.
                .requestMatchers(HttpMethod.POST, "/api/v1/trips")
                .hasRole("GUEST")
                .requestMatchers(HttpMethod.GET, "/api/v1/trips/*")
                .hasRole("GUEST")
                // Ending a trip at a hub (RIA-55): guests only.
                .requestMatchers(HttpMethod.POST, "/api/v1/trips/*/end")
                .hasRole("GUEST")
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(resourceServer ->
            resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .build();
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
  }
}
