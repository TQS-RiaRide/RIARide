package com.riaride.acceptance;

import static io.restassured.RestAssured.given;

import io.cucumber.spring.ScenarioScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

// Creates synthetic users in the Keycloak test container and logs them in.
// The password grant is enabled only on a client created here, at test time;
// the realm in infra/keycloak never allows it.
// Scenario-scoped so that other Spring tests, which also scan this package,
// never create it and never start Keycloak.
@Component
@ScenarioScope
public class KeycloakTestUsers {

	private static final String TEST_CLIENT = "riaride-acceptance-tests";
	private static final String SPA_CLIENT = "riaride-spa";

	private final RealmResource realm;

	public KeycloakTestUsers() {
		this.realm = CucumberSpringConfiguration.KEYCLOAK.getKeycloakAdminClient()
			.realm(CucumberSpringConfiguration.REALM);
		createTestClientIfMissing();
	}

	// The test client issues the same claims as the SPA: it copies the SPA's token mappers
	// (hotel_id) from the realm file, so the tests exercise the real realm configuration.
	private void createTestClientIfMissing() {
		if (!realm.clients().findByClientId(TEST_CLIENT).isEmpty()) {
			return;
		}
		var spa = realm.clients().findByClientId(SPA_CLIENT).getFirst();
		var client = new ClientRepresentation();
		client.setClientId(TEST_CLIENT);
		client.setPublicClient(true);
		client.setDirectAccessGrantsEnabled(true);
		client.setStandardFlowEnabled(false);
		var mappers = spa.getProtocolMappers();
		for (var mapper : mappers) {
			mapper.setId(null);
		}
		client.setProtocolMappers(mappers);
		realm.clients().create(client).close();
	}

	// Creates a user with one realm role and, when given, the hotel they manage,
	// and returns an access token for them.
	public String logIn(String fullName, String role, String hotelId) {
		var username = "user-" + UUID.randomUUID();
		var password = UUID.randomUUID().toString();
		createUser(username, password, fullName, role, hotelId);
		return given()
			.baseUri(CucumberSpringConfiguration.KEYCLOAK.getAuthServerUrl())
			.port(CucumberSpringConfiguration.KEYCLOAK.getHttpPort())
			.basePath("")
			.formParams(Map.of(
				"grant_type", "password",
				"client_id", TEST_CLIENT,
				"scope", "openid",
				"username", username,
				"password", password))
			.when()
			.post("/realms/" + CucumberSpringConfiguration.REALM + "/protocol/openid-connect/token")
			.then()
			.statusCode(200)
			.extract()
			.path("access_token");
	}

	private void createUser(String username, String password, String fullName, String role, String hotelId) {
		var names = fullName.split(" ", 2);
		var user = new UserRepresentation();
		user.setUsername(username);
		user.setEmail(username + "@example.test");
		user.setEmailVerified(true);
		user.setFirstName(names[0]);
		user.setLastName(names.length > 1 ? names[1] : names[0]);
		user.setEnabled(true);
		if (hotelId != null) {
			user.setAttributes(Map.of("hotel_id", List.of(hotelId)));
		}
		var credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue(password);
		credential.setTemporary(false);
		user.setCredentials(List.of(credential));

		String userId;
		try (var response = realm.users().create(user)) {
			userId = CreatedResponseUtil.getCreatedId(response);
		}
		var realmRole = realm.roles().get(role).toRepresentation();
		realm.users().get(userId).roles().realmLevel().add(List.of(realmRole));
	}

}
