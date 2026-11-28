package com.riaride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Integration test (Failsafe): the API documentation (RIA-107). The OpenAPI document and Swagger UI
// are public, the document describes every endpoint of the application (as RoleAccessMatrixIT
// finds them), and it declares the Keycloak bearer token. A new endpoint is documented without
// any change here; an endpoint left out of the document fails this test.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationIT {

  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private String apiDocs() throws Exception {
    return mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Requirement("RIA-107")
  @Test
  void shouldDocumentEveryEndpointOfTheApplication() throws Exception {
    var applicationEndpoints =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(
                entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.riaride"))
            .flatMap(entry ->
                entry.getKey().getMethodsCondition().getMethods().stream()
                    .flatMap(method ->
                        entry.getKey().getPatternValues().stream()
                            .map(pattern -> method.name() + " " + pattern)))
            .collect(Collectors.toSet());
    Map<String, Map<String, Object>> paths = JsonPath.read(apiDocs(), "$.paths");
    var documentedEndpoints =
        paths.entrySet().stream()
            .flatMap(path ->
                path.getValue().keySet().stream()
                    .map(method -> method.toUpperCase(Locale.ROOT) + " " + path.getKey()))
            .collect(Collectors.toSet());

    assertThat(documentedEndpoints).containsExactlyInAnyOrderElementsOf(applicationEndpoints);
  }

  @Requirement("RIA-107")
  @Test
  void shouldDeclareTheKeycloakBearerToken() throws Exception {
    var docs = apiDocs();

    assertThat(JsonPath.<String>read(docs, "$.info.title")).isEqualTo("RiaRide API");
    assertThat(JsonPath.<String>read(docs, "$.components.securitySchemes.keycloak.scheme"))
        .isEqualTo("bearer");
  }

  @Requirement("RIA-107")
  @Test
  void shouldServeSwaggerUiWithoutAToken() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
