package com.riaride.accounts.internal.keycloak;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.internal.application.IdentityProviderUnavailableException;
import com.riaride.accounts.internal.application.IdentityRejectedException;
import com.riaride.accounts.internal.application.NewGuestUser;
import java.net.URI;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Unit test (Surefire): only a 400 on the user-creation call means Keycloak refused the account's
// details (422). A 400 anywhere else in the flow is a misconfiguration or an outage (503), not
// the guest's fault (review of #28).
class KeycloakIdentityProviderTest {

	private static final String BASE = "http://keycloak.test";
	private static final String USERS = BASE + "/admin/realms/riaride/users";
	private static final NewGuestUser USER = new NewGuestUser("marta.sousa@example.test", "Salt-Pans-2026", "Marta", "Sousa");

	private MockRestServiceServer keycloak;
	private KeycloakIdentityProvider identity;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder();
		keycloak = MockRestServiceServer.bindTo(builder).build();
		identity = new KeycloakIdentityProvider(builder,
			new KeycloakAdminProperties(BASE, "riaride", "riaride-backend", "secret"), Clock.systemUTC());
	}

	private void givenToken() {
		keycloak.expect(requestTo(BASE + "/realms/riaride/protocol/openid-connect/token"))
			.andRespond(withSuccess("{\"access_token\": \"token\"}", MediaType.APPLICATION_JSON));
	}

	private void givenNoUserWithTheEmail() {
		keycloak.expect(requestTo(startsWith(USERS + "?email=")))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
	}

	@Requirement("RIA-51")
	@Test
	void shouldReportAccountRejectedWhenKeycloakRefusesTheNewUser() {
		givenToken();
		givenNoUserWithTheEmail();
		keycloak.expect(requestTo(USERS)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());

		assertThatThrownBy(() -> identity.createGuestUser(USER)).isInstanceOf(IdentityRejectedException.class);
	}

	// For example unauthorized_client when the service account is disabled.
	@Requirement("RIA-51")
	@Test
	void shouldReportUnavailableWhenTheTokenRequestGetsBadRequest() {
		keycloak.expect(requestTo(BASE + "/realms/riaride/protocol/openid-connect/token")).andRespond(withBadRequest());

		assertThatThrownBy(() -> identity.createGuestUser(USER)).isInstanceOf(IdentityProviderUnavailableException.class);
	}

	@Requirement("RIA-51")
	@Test
	void shouldReportUnavailableWhenTheUserLookupGetsBadRequest() {
		givenToken();
		keycloak.expect(requestTo(startsWith(USERS + "?email="))).andRespond(withBadRequest());

		assertThatThrownBy(() -> identity.createGuestUser(USER)).isInstanceOf(IdentityProviderUnavailableException.class);
	}

	@Requirement("RIA-51")
	@Test
	void shouldReportUnavailableAndDeleteTheUserWhenTheRoleMappingGetsBadRequest() {
		givenToken();
		givenNoUserWithTheEmail();
		keycloak.expect(requestTo(USERS)).andExpect(method(HttpMethod.POST))
			.andRespond(withCreatedEntity(URI.create(USERS + "/u-1")));
		keycloak.expect(requestTo(USERS + "/u-1/role-mappings/realm/available")).andRespond(withBadRequest());
		keycloak.expect(requestTo(USERS + "/u-1")).andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		assertThatThrownBy(() -> identity.createGuestUser(USER)).isInstanceOf(IdentityProviderUnavailableException.class);
		keycloak.verify();
	}

}
