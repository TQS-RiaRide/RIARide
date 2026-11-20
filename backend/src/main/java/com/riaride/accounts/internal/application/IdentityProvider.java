package com.riaride.accounts.internal.application;

// Port to the identity provider that holds guests' credentials (Keycloak, ADR 0009 and 0010).
public interface IdentityProvider {

	// Creates an enabled user with the GUEST role. If a user with the same email is an orphan
	// guest (only the GUEST role, and older than any sign-up still in progress), it is reused
	// with the new password, and GuestUser.created is false. Throws EmailAlreadyRegisteredException
	// when the email belongs to any other user, and IdentityRejectedException when the identity
	// provider refuses the details.
	GuestUser createGuestUser(NewGuestUser user);

	// Deletes the user; deleting a user that no longer exists is not an error.
	void deleteUser(String userId);

}
