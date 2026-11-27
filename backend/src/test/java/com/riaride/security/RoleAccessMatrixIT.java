package com.riaride.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.riaride.TestKeycloak;
import com.riaride.TestcontainersConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Integration test (Failsafe): who may call each endpoint of the API (RIA-86). Every endpoint
// is called by every role with a real access token from Keycloak in Testcontainers, validated by
// the application against Keycloak's JWKS, over HTTP with REST Assured.
//
// A role that may call an endpoint never gets 401 or 403, nor a server error: the body sent is
// empty, so the answer may be 400 or 404, but the request got past authorization. A user with no
// platform role may only read their own account. Every other role gets 403, and a
// request with no token, or a token Keycloak did not issue, gets 401; both as problem details.
// A forbidden role must get 403 whatever the body, before it is read or validated, so it learns
// nothing about the input rules.
//
// The matrix must list every endpoint of the application: a new endpoint fails
// shouldCoverEveryEndpointOfTheApplication until its roles are added here.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAccessMatrixIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String ANY_ID = "00000000-0000-0000-0000-0000000000ff";

  // The platform roles, plus a Keycloak user with no platform role (only Keycloak's defaults):
  // such a user may read their own account but nothing else.
  enum Role {
    GUEST,
    OPERATOR,
    TECHNICIAN,
    HOTEL_MANAGER,
    ADMIN,
    NO_ROLE;

    String realmRole() {
      return this == NO_ROLE ? null : name();
    }
  }

  record Endpoint(String method, String pattern, String path, Set<Role> allowed) {

    @Override
    public String toString() {
      return method + " " + pattern;
    }
  }

  private static final Set<Role> EVERY_ROLE = Set.of(Role.values());

  // Roles per endpoint: Product Specification, API section (05-api.md).
  private static final List<Endpoint> ENDPOINTS =
      List.of(
          new Endpoint("GET", "/api/v1/me", "/api/v1/me", EVERY_ROLE),
          new Endpoint(
              "POST", "/api/v1/stays", "/api/v1/stays", Set.of(Role.HOTEL_MANAGER, Role.ADMIN)),
          new Endpoint("POST", "/api/v1/hubs", "/api/v1/hubs", Set.of(Role.OPERATOR, Role.ADMIN)),
          new Endpoint(
              "POST",
              "/api/v1/hubs/{hubId}/vehicles",
              "/api/v1/hubs/" + ANY_ID + "/vehicles",
              Set.of(Role.OPERATOR, Role.ADMIN)),
          new Endpoint(
              "GET",
              "/api/v1/hubs/{hubId}/vehicles",
              "/api/v1/hubs/" + ANY_ID + "/vehicles",
              Set.of(Role.GUEST, Role.OPERATOR, Role.ADMIN)),
          new Endpoint("POST", "/api/v1/passes", "/api/v1/passes", Set.of(Role.GUEST)),
          new Endpoint(
              "PUT",
              "/api/v1/vehicles/{id}/status",
              "/api/v1/vehicles/" + ANY_ID + "/status",
              Set.of(Role.OPERATOR, Role.ADMIN)),
          new Endpoint("POST", "/api/v1/trips", "/api/v1/trips", Set.of(Role.GUEST)),
          new Endpoint("GET", "/api/v1/trips/{id}", "/api/v1/trips/" + ANY_ID, Set.of(Role.GUEST)),
          new Endpoint(
              "POST",
              "/api/v1/trips/{id}/end",
              "/api/v1/trips/" + ANY_ID + "/end",
              Set.of(Role.GUEST)),
          new Endpoint(
              "POST", "/api/v1/tickets", "/api/v1/tickets", Set.of(Role.OPERATOR, Role.ADMIN)));

  // Forbidden roles get 403 even when the path or query is malformed, before it is converted.
  private static final List<Endpoint> MALFORMED_REQUESTS =
      List.of(
          new Endpoint(
              "POST",
              "/api/v1/hubs/{hubId}/vehicles",
              "/api/v1/hubs/not-a-uuid/vehicles",
              Set.of(Role.OPERATOR, Role.ADMIN)),
          new Endpoint(
              "GET",
              "/api/v1/hubs/{hubId}/vehicles",
              "/api/v1/hubs/not-a-uuid/vehicles?type=NOT_A_TYPE",
              Set.of(Role.GUEST, Role.OPERATOR, Role.ADMIN)),
          new Endpoint(
              "PUT",
              "/api/v1/vehicles/{id}/status",
              "/api/v1/vehicles/not-a-uuid/status",
              Set.of(Role.OPERATOR, Role.ADMIN)),
          new Endpoint("GET", "/api/v1/trips/{id}", "/api/v1/trips/not-a-uuid", Set.of(Role.GUEST)),
          new Endpoint(
              "POST",
              "/api/v1/trips/{id}/end",
              "/api/v1/trips/not-a-uuid/end",
              Set.of(Role.GUEST)));

  // Open to everyone, with or without a token.
  private static final List<Endpoint> PUBLIC_ENDPOINTS =
      List.of(new Endpoint("POST", "/api/v1/guests", "/api/v1/guests", EVERY_ROLE));

  @DynamicPropertySource
  static void keycloakProperties(DynamicPropertyRegistry registry) {
    TestKeycloak.registerProperties(registry);
  }

  @LocalServerPort private int port;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private final Map<Role, TestKeycloak.TestUser> users = new EnumMap<>(Role.class);

  @BeforeAll
  void logInOneUserPerRole() {
    for (var role : Role.values()) {
      users.put(role, TestKeycloak.logInWithRole(role.realmRole()));
    }
  }

  @AfterAll
  void deleteTheUsers() {
    users.values().forEach(user -> TestKeycloak.deleteUser(user.id()));
  }

  private String token(Role role) {
    return users.get(role).accessToken();
  }

  private RequestSpecification request() {
    return given().port(port).basePath("").contentType(ContentType.JSON).body("{}");
  }

  private static Response send(RequestSpecification request, Endpoint endpoint) {
    return request.when().request(endpoint.method(), endpoint.path());
  }

  static Stream<Arguments> everyRoleOnEveryEndpoint() {
    return ENDPOINTS.stream()
        .flatMap(endpoint -> Stream.of(Role.values()).map(role -> Arguments.of(endpoint, role)));
  }

  static Stream<Arguments> forbiddenRolesOnMalformedRequests() {
    return MALFORMED_REQUESTS.stream()
        .flatMap(endpoint ->
            Stream.of(Role.values())
                .filter(role -> !endpoint.allowed().contains(role))
                .map(role -> Arguments.of(endpoint, role)));
  }

  static Stream<Endpoint> protectedEndpoints() {
    return ENDPOINTS.stream();
  }

  static Stream<Endpoint> publicEndpoints() {
    return PUBLIC_ENDPOINTS.stream();
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{1} on {0}")
  @MethodSource("everyRoleOnEveryEndpoint")
  void shouldLetOnlyTheEndpointRolesThroughAndRefuseTheOthersWith403(Endpoint endpoint, Role role) {
    var response = send(request().auth().oauth2(token(role)), endpoint);

    if (endpoint.allowed().contains(role)) {
      assertThat(response.statusCode()).as("%s may call %s", role, endpoint).isNotIn(401, 403);
    } else {
      assertThat(response.statusCode()).as("%s may not call %s", role, endpoint).isEqualTo(403);
      assertThat(response.contentType()).startsWith(PROBLEM_JSON);
      assertThat(response.jsonPath().getInt("status")).isEqualTo(403);
    }
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{1} on {0}")
  @MethodSource("forbiddenRolesOnMalformedRequests")
  void shouldRefuseAForbiddenRoleWith403EvenWhenThePathIsMalformed(Endpoint endpoint, Role role) {
    var response = send(request().auth().oauth2(token(role)), endpoint);

    assertThat(response.statusCode()).as("%s on %s", role, endpoint.path()).isEqualTo(403);
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{0}")
  @MethodSource("protectedEndpoints")
  void shouldRefuseARequestWithoutATokenWith401(Endpoint endpoint) {
    var response = send(request(), endpoint);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.header("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(response.contentType()).startsWith(PROBLEM_JSON);
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{0}")
  @MethodSource("protectedEndpoints")
  void shouldRefuseATokenWhoseRolesWereEditedWith401(Endpoint endpoint) {
    var guestTokenMadeAdmin = withPayloadEdited(token(Role.GUEST), "\"GUEST\"", "\"ADMIN\"");

    var response = send(request().auth().oauth2(guestTokenMadeAdmin), endpoint);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.header("WWW-Authenticate")).contains("invalid_token");
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{0}")
  @MethodSource("protectedEndpoints")
  void shouldRefuseATokenSignedWithAnotherKeyWith401(Endpoint endpoint) throws Exception {
    var response = send(request().auth().oauth2(adminTokenSignedByAnotherIssuer()), endpoint);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.header("WWW-Authenticate")).contains("invalid_token");
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{0}")
  @MethodSource("protectedEndpoints")
  void shouldRefuseATokenThatIsNotAJwtWith401(Endpoint endpoint) {
    var response = send(request().auth().oauth2("not-a-jwt"), endpoint);

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Requirement("RIA-86")
  @ParameterizedTest(name = "{0}")
  @MethodSource("publicEndpoints")
  void shouldLetAnyoneCallAPublicEndpointWithoutAToken(Endpoint endpoint) {
    assertThat(send(request(), endpoint).statusCode()).isNotIn(401, 403).isLessThan(500);
  }

  @Requirement("RIA-86")
  @Test
  void shouldLetAnyoneReadTheHealthWithoutAToken() {
    given().port(port).basePath("").when().get("/actuator/health").then().statusCode(200);
  }

  @Requirement("RIA-86")
  @Test
  void shouldCoverEveryEndpointOfTheApplication() {
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
    var listedEndpoints =
        Stream.concat(ENDPOINTS.stream(), PUBLIC_ENDPOINTS.stream())
            .map(Endpoint::toString)
            .collect(Collectors.toSet());

    assertThat(applicationEndpoints).containsExactlyInAnyOrderElementsOf(listedEndpoints);
  }

  // Changes the token's claims without signing it again, as an attacker would.
  private static String withPayloadEdited(String token, String from, String to) {
    var parts = token.split("\\.");
    var decoder = Base64.getUrlDecoder();
    var encoder = Base64.getUrlEncoder().withoutPadding();
    var payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);
    assertThat(payload).contains(from);
    var edited = encoder.encodeToString(payload.replace(from, to).getBytes(StandardCharsets.UTF_8));
    return parts[0] + "." + edited + "." + parts[2];
  }

  // A well-formed token with the admin role and the realm's issuer, but signed with a key that
  // Keycloak does not publish.
  private static String adminTokenSignedByAnotherIssuer() throws Exception {
    var key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(TestKeycloak.container().getAuthServerUrl() + "/realms/" + TestKeycloak.REALM)
            .subject(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("realm_access", Map.of("roles", List.of("ADMIN")))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
