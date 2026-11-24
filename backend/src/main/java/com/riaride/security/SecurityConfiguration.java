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

// Stateless bearer-token API. Every endpoint needs a valid Keycloak token except health;
// modules restrict their endpoints by role with @PreAuthorize. An endpoint whose callers must be
// refused before the body is read and validated also gets a role rule here, so a caller without
// the role gets 403 and learns nothing about the input rules.
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, JsonMapper json) {
    var problems = new ProblemDetailsSecurityHandler(json);
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
                // Hub registration (RIA-56): operators and admins only.
                .requestMatchers(HttpMethod.POST, "/api/v1/hubs")
                .hasAnyRole("OPERATOR", "ADMIN")
                // Vehicle registration at a hub (RIA-57): operators and admins only.
                .requestMatchers(HttpMethod.POST, "/api/v1/hubs/*/vehicles")
                .hasAnyRole("OPERATOR", "ADMIN")
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
