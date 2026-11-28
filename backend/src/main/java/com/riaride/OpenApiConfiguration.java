package com.riaride;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The OpenAPI document of the REST API (RIA-107), served at /v3/api-docs and shown by Swagger UI at
// /swagger-ui.html. springdoc builds the paths from the controllers; this adds the title and the
// Keycloak bearer token every endpoint needs, except guest sign-up (POST /api/v1/guests).
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

  static final String BEARER = "keycloak";

  @Bean
  OpenAPI riaRideOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("RiaRide API")
                .version("v1")
                .description(
                    "Light-mobility fleet for Grupo Hoteleiro Marés in Vila Ria. Errors are RFC"
                        + " 9457 problem details; a 400 names each invalid field in"
                        + " invalid-params. Roles per endpoint: Product Specification,"
                        + " section 5."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("An access token issued by the riaride realm in Keycloak.")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}
