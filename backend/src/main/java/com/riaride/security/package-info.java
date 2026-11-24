// Authentication and authorization shared by every module: the backend is an OAuth2
// resource server for the Keycloak realm, and roles come from the token's realm roles.
@org.springframework.modulith.ApplicationModule(displayName = "Security")
package com.riaride.security;
