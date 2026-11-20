package com.riaride;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.Map;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.test.context.DynamicPropertyRegistry;

// One Keycloak test container per JVM, with the realm from infra/keycloak, shared by every
// test that needs it (acceptance and integration tests), so it starts only once per build.
public final class TestKeycloak {

	public static final String REALM = "riaride";
	public static final String BACKEND_CLIENT = "riaride-backend";
	// Allows the password grant, so tests can check a password; created here, never in the realm file.
	private static final String LOGIN_CHECK_CLIENT = "riaride-login-check-tests";

	private static final KeycloakContainer CONTAINER = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.0")
		.withRealmImportFile("/keycloak/riaride-realm.json");

	private TestKeycloak() {
	}

	public static synchronized KeycloakContainer container() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		return CONTAINER;
	}

	public static RealmResource realm() {
		return container().getKeycloakAdminClient().realm(REALM);
	}

	// Keycloak generates the backend client's secret when it imports the realm.
	public static String backendClientSecret() {
		var clients = realm().clients();
		var id = clients.findByClientId(BACKEND_CLIENT).getFirst().getId();
		return clients.get(id).getSecret().getValue();
	}

	public static List<UserRepresentation> usersWithEmail(String email) {
		return realm().users().searchByEmail(email, true);
	}

	public static void deleteUsersWithEmail(String email) {
		for (var user : usersWithEmail(email)) {
			realm().users().delete(user.getId()).close();
		}
	}

	// True when Keycloak accepts these credentials (password grant on a test-only client).
	public static boolean canLogIn(String username, String password) {
		createLoginCheckClientIfMissing();
		var status = given()
			.baseUri(container().getAuthServerUrl())
			.port(container().getHttpPort())
			.basePath("")
			.formParams(Map.of(
				"grant_type", "password",
				"client_id", LOGIN_CHECK_CLIENT,
				"username", username,
				"password", password))
			.when()
			.post("/realms/" + REALM + "/protocol/openid-connect/token")
			.statusCode();
		return status == 200;
	}

	private static synchronized void createLoginCheckClientIfMissing() {
		if (!realm().clients().findByClientId(LOGIN_CHECK_CLIENT).isEmpty()) {
			return;
		}
		var client = new ClientRepresentation();
		client.setClientId(LOGIN_CHECK_CLIENT);
		client.setPublicClient(true);
		client.setDirectAccessGrantsEnabled(true);
		client.setStandardFlowEnabled(false);
		realm().clients().create(client).close();
	}

	// Points the application's token validation and its Admin API client at the container.
	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
			() -> container().getAuthServerUrl() + "/realms/" + REALM);
		registry.add("riaride.keycloak.admin.base-url", () -> container().getAuthServerUrl());
		registry.add("riaride.keycloak.admin.realm", () -> REALM);
		registry.add("riaride.keycloak.admin.client-id", () -> BACKEND_CLIENT);
		registry.add("riaride.keycloak.admin.client-secret", TestKeycloak::backendClientSecret);
	}

}
