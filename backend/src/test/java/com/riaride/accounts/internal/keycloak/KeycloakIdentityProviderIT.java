package com.riaride.accounts.internal.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestKeycloak;
import com.riaride.accounts.internal.application.EmailAlreadyRegisteredException;
import com.riaride.accounts.internal.application.IdentityRejectedException;
import com.riaride.accounts.internal.application.NewGuestUser;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.web.client.RestClient;

// Integration test (Failsafe): the Keycloak adapter against a Keycloak container with the
// realm from infra/keycloak, through the backend's own service client and its permissions.
class KeycloakIdentityProviderIT {

	private static final String EMAIL = "marta.sousa@example.test";
	private static final String PASSWORD = "Salt-Pans-2026";

	private KeycloakAdminProperties properties;
	private KeycloakIdentityProvider identity;

	@BeforeEach
	void setUp() {
		var keycloak = TestKeycloak.container();
		properties = new KeycloakAdminProperties(keycloak.getAuthServerUrl(), TestKeycloak.REALM,
			TestKeycloak.BACKEND_CLIENT, TestKeycloak.backendClientSecret());
		identity = identityAt(Clock.systemUTC());
		TestKeycloak.deleteUsersWithEmail(EMAIL);
	}

	private KeycloakIdentityProvider identityAt(Clock clock) {
		return new KeycloakIdentityProvider(RestClient.builder(), properties, clock);
	}

	// Later than any sign-up still in progress, so a guest-only user left in Keycloak is an orphan.
	private KeycloakIdentityProvider identityLater() {
		return identityAt(Clock.offset(Clock.systemUTC(), KeycloakIdentityProvider.ORPHAN_MIN_AGE.plus(Duration.ofMinutes(1))));
	}

	@AfterEach
	void tearDown() {
		TestKeycloak.deleteUsersWithEmail(EMAIL);
	}

	private static List<String> realmRolesOf(String userId) {
		return TestKeycloak.realm().users().get(userId).roles().realmLevel().listAll().stream()
			.map(RoleRepresentation::getName)
			.toList();
	}

	private static String createUserWithRole(String role) {
		var user = new UserRepresentation();
		user.setUsername(EMAIL);
		user.setEmail(EMAIL);
		user.setFirstName("Marta");
		user.setLastName("Sousa");
		user.setEnabled(true);
		var credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue("Old-Password-1");
		user.setCredentials(List.of(credential));
		String id;
		try (var response = TestKeycloak.realm().users().create(user)) {
			id = CreatedResponseUtil.getCreatedId(response);
		}
		if (role != null) {
			var realmRole = TestKeycloak.realm().roles().get(role).toRepresentation();
			TestKeycloak.realm().users().get(id).roles().realmLevel().add(List.of(realmRole));
		}
		return id;
	}

	@Requirement("RIA-51")
	@Test
	void shouldCreateEnabledUserWithGuestRoleAndNames() {
		var created = identity.createGuestUser(new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa"));

		assertThat(created.created()).isTrue();
		var id = created.id();
		var user = TestKeycloak.realm().users().get(id).toRepresentation();
		assertThat(user.getEmail()).isEqualTo(EMAIL);
		assertThat(user.getUsername()).isEqualTo(EMAIL);
		assertThat(user.getFirstName()).isEqualTo("Marta");
		assertThat(user.getLastName()).isEqualTo("Sousa");
		assertThat(user.isEnabled()).isTrue();
		assertThat(realmRolesOf(id)).contains("GUEST");
	}

	@Requirement("RIA-51")
	@Test
	void shouldSetPasswordSoGuestCanLogIn() {
		identity.createGuestUser(new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa"));

		assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isTrue();
	}

	// ADR 0010: a sign-up that crashed after creating the Keycloak user left an orphan guest user.
	@Requirement("RIA-51")
	@Test
	void shouldReuseExistingGuestOnlyUserWithNewPassword() {
		var orphan = createUserWithRole("GUEST");

		var reused = identityLater().createGuestUser(new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa"));

		assertThat(reused.id()).isEqualTo(orphan);
		assertThat(reused.created()).isFalse();
		assertThat(TestKeycloak.usersWithEmail(EMAIL)).hasSize(1);
		assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isTrue();
	}

	@Requirement("RIA-51")
	@Test
	void shouldRefuseEmailOfUserWithAnotherRole() {
		var operator = createUserWithRole("OPERATOR");
		var user = new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa");
		var later = identityLater();

		assertThatThrownBy(() -> later.createGuestUser(user)).isInstanceOf(EmailAlreadyRegisteredException.class);
		assertThat(realmRolesOf(operator)).doesNotContain("GUEST");
		assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isFalse();
	}

	// A guest-only user created moments ago may belong to a sign-up still in progress
	// (a double click): it is not taken over, and its password is left as it is.
	@Requirement("RIA-51")
	@Test
	void shouldRefuseRecentGuestUserThatMayBeAnotherSignUpInProgress() {
		createUserWithRole("GUEST");
		var user = new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa");

		assertThatThrownBy(() -> identity.createGuestUser(user)).isInstanceOf(EmailAlreadyRegisteredException.class);
		assertThat(TestKeycloak.canLogIn(EMAIL, "Old-Password-1")).isTrue();
		assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isFalse();
	}

	// Only users the sign-up flow left behind carry GUEST; a user created by hand has no app role.
	@Requirement("RIA-51")
	@Test
	void shouldRefuseUserWithoutTheGuestRole() {
		createUserWithRole(null);
		var user = new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa");
		var later = identityLater();

		assertThatThrownBy(() -> later.createGuestUser(user)).isInstanceOf(EmailAlreadyRegisteredException.class);
		assertThat(TestKeycloak.canLogIn(EMAIL, PASSWORD)).isFalse();
	}

	// For example a password policy added to the realm later: a client error, not an outage.
	@Requirement("RIA-51")
	@Test
	void shouldReportAccountThatKeycloakRejectsAsRejectedNotUnavailable() {
		var realm = TestKeycloak.realm();
		var representation = realm.toRepresentation();
		var previousPolicy = representation.getPasswordPolicy();
		representation.setPasswordPolicy("length(20)");
		realm.update(representation);
		try {
			var user = new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa");

			assertThatThrownBy(() -> identity.createGuestUser(user)).isInstanceOf(IdentityRejectedException.class);
			assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
		} finally {
			representation.setPasswordPolicy(previousPolicy == null ? "" : previousPolicy);
			realm.update(representation);
		}
	}

	@Requirement("RIA-51")
	@Test
	void shouldDeleteUser() {
		var id = identity.createGuestUser(new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa")).id();

		identity.deleteUser(id);

		assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
	}

	@Requirement("RIA-51")
	@Test
	void shouldIgnoreDeletingUserThatNoLongerExists() {
		var id = identity.createGuestUser(new NewGuestUser(EMAIL, PASSWORD, "Marta", "Sousa")).id();
		identity.deleteUser(id);

		identity.deleteUser(id);

		assertThat(TestKeycloak.usersWithEmail(EMAIL)).isEmpty();
	}

}
