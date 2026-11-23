package com.riaride.accounts;

import java.util.Optional;
import java.util.UUID;

// Public API of the accounts module for other modules that act on behalf of a guest.
public interface GuestDirectory {

  // The guest linked to this Keycloak user, if their stay is active today in Vila Ria.
  // Empty when the user is not a guest or their stay has ended.
  Optional<ActiveGuest> findActiveGuest(String keycloakId);

  // The guest linked to this Keycloak user, whatever the state of their stay.
  Optional<UUID> findGuestId(String keycloakId);
}
