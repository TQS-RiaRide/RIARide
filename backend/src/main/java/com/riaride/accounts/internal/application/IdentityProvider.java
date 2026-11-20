package com.riaride.accounts.internal.application;

// Port to the identity provider that holds guests' credentials (Keycloak, ADR 0009 and 0010).
public interface IdentityProvider {

	// Creates an enabled user with the GUEST role and returns its id. If a user with the same
	// email exists only as a guest (left behind by an earlier sign-up that failed locally),
	// it is reused with the new password. Throws EmailAlreadyRegisteredException when the
	// email belongs to any other user.
	String createGuestUser(NewGuestUser user);

	// Deletes the user; deleting a user that no longer exists is not an error.
	void deleteUser(String userId);

}
